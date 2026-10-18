package com.owo233.tcqt.host.service

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import top.artmoe.inao.entries.RecallPushOuterClass.C2CRecall
import top.artmoe.inao.entries.RecallPushOuterClass.GroupRecall
import top.artmoe.inao.entries.RecallPushOuterClass.RecallMessage
import java.io.ByteArrayOutputStream

/** Parses only recall metadata; all unmodified fields retain their original wire bytes. */
internal object RecallPacketParser {
    data class Recall(
        val group: Boolean,
        val peerId: String,
        val msgSeq: Long,
        val operatorUid: String,
        val senderUid: String,
        val msgId: Long = 0,
        val senderMsgSeq: Long = 0,
        val msgTime: Long = 0,
        val msgRandom: Long = 0,
    )

    data class Result(
        val bytes: ByteArray,
        val recalls: List<Recall> = emptyList(),
        val blockPush: Boolean = false,
    )

    private data class Field(val number: Int, val wire: Int, val raw: ByteArray, val payload: ByteArray)
    private data class Operation(val bytes: ByteArray, val recalls: List<Recall>, val empty: Boolean)
    private const val GROUP_HEADER_SIZE = 7

    fun msgPush(bytes: ByteArray, currentUid: String): Result {
        val fields = fields(bytes) ?: return Result(bytes)
        // A MsgPush has one message. Do not suppress an unexpected multi-message envelope.
        val messages = fields.filter { it.number == 1 && it.wire == 2 }
        if (messages.size != 1) return Result(bytes)
        val message = messages.single()
        val op = operation(message.payload, currentUid) ?: return Result(bytes)
        if (op.empty) return Result(bytes, op.recalls, blockPush = true)
        val rewritten = rewriteOperation(message.payload, op.bytes) ?: return Result(bytes)
        return Result(join(fields.map { if (it === message) encoded(1, rewritten) else it.raw }), op.recalls)
    }

    fun infoSyncPush(bytes: ByteArray, currentUid: String): Result {
        val recalls = mutableListOf<Recall>()
        // 8 = system notifications, 4 = notification body, 8 = QQMessage.
        // push_flag also carries unrelated sync state; inspect the actual messages instead.
        val rewritten = rewriteChildren(bytes, 8) { content ->
            rewriteChildren(content, 4) { body ->
                rewriteChildren(body, 8) { message ->
                    val op = operation(message, currentUid)
                    if (op == null) message else if (op.empty) {
                        recalls.addAll(op.recalls)
                        null
                    } else {
                        val replacement = rewriteOperation(message, op.bytes)
                        if (replacement != null) recalls.addAll(op.recalls)
                        replacement ?: message
                    }
                }
            }
        }
        return Result(if (recalls.isEmpty()) bytes else rewritten, recalls)
    }

    private fun operation(bytes: ByteArray, currentUid: String): Operation? = runCatching {
        val msg = RecallMessage.parseFrom(bytes)
        val content = msg.content
        val subtype = if (content.msgSubType != 0) content.msgSubType else content.subSeq
        val op = msg.body.operationInfo.toByteArray()
        when (content.msgType) {
            528 if subtype == 138 -> c2c(op, msg, currentUid)
            732 if subtype == 17 -> group(op, currentUid)
            else -> null
        }
    }.getOrNull()

    private fun c2c(bytes: ByteArray, msg: RecallMessage, currentUid: String): Operation? {
        val parsed = C2CRecall.parseFrom(bytes)
        val fields = fields(bytes) ?: return null
        val infos = fields.filter { it.number == 1 && it.wire == 2 }
        if (infos.size != parsed.infoCount) return null
        val recalls = mutableListOf<Recall>()
        val removed = mutableSetOf<Field>()
        infos.zip(parsed.infoList).forEach { (field, info) ->
            val operator = info.operatorUid.uid()
            val receiver = info.receiverUid.uid()
            val peer = when {
                operator.isNotBlank() && operator != currentUid -> operator
                receiver.isNotBlank() && receiver != currentUid -> receiver
                else -> listOf(msg.head.senderUid.uid(), msg.head.receiverUid.uid())
                    .firstOrNull { it.isNotBlank() && it != currentUid }.orEmpty()
            }
            val seq = info.msgSeq.toUInt().toLong()
            if (operator.isNotBlank() && operator != currentUid && peer.isNotBlank() && seq > 0) {
                removed.add(field)
                recalls.add(Recall(
                    false, peer, seq, operator, operator, info.msgId,
                    info.senderMsgSeq.toUInt().toLong(), info.msgTime, info.msgRandom,
                ))
            }
        }
        if (recalls.isEmpty()) return null
        return Operation(join(fields.filterNot { it in removed }.map { it.raw }), recalls, removed.size == infos.size)
    }

    private fun group(bytes: ByteArray, currentUid: String): Operation? {
        // Group operations have a 7-byte binary header, not a protobuf prefix.
        if (bytes.size <= GROUP_HEADER_SIZE) return null
        val op = GroupRecall.parseFrom(bytes.copyOfRange(GROUP_HEADER_SIZE, bytes.size))
        val operator = op.info.operatorUid.uid()
        if (op.peerId <= 0 || operator.isBlank() || operator == currentUid) return null
        val recalls = op.info.msgInfoList.mapNotNull { msg ->
            val seq = msg.msgSeq.toUInt().toLong()
            if (seq == 0L) null else Recall(
                true, op.peerId.toString(), seq, operator, msg.senderUid.uid(),
                msgTime = msg.msgTime, msgRandom = msg.msgRandom,
            )
        }
        // An incomplete target must not make us drop other operation data we cannot identify.
        if (recalls.isEmpty() || recalls.size != op.info.msgInfoCount) return null
        return Operation(bytes, recalls, empty = true)
    }

    private fun ByteString.uid(): String = if (isValidUtf8) toStringUtf8() else ""

    private fun rewriteOperation(message: ByteArray, operation: ByteArray): ByteArray? {
        if (fields(message) == null) return null
        return rewriteChildren(message, 3) { body ->
            rewriteChildren(body, 2) { operation }
        }
    }

    private fun rewriteChildren(bytes: ByteArray, number: Int, transform: (ByteArray) -> ByteArray?): ByteArray {
        val fields = fields(bytes) ?: return bytes
        var changed = false
        val result = fields.mapNotNull { field ->
            if (field.number != number || field.wire != 2) return@mapNotNull field.raw
            val replacement = transform(field.payload)
            when {
                replacement == null -> { changed = true; null }
                replacement.contentEquals(field.payload) -> field.raw
                else -> { changed = true; encoded(number, replacement) }
            }
        }
        return if (changed) join(result) else bytes
    }

    private fun fields(bytes: ByteArray): List<Field>? = runCatching {
        val input = CodedInputStream.newInstance(bytes)
        buildList {
            while (!input.isAtEnd) {
                val start = input.totalBytesRead
                val tag = input.readTag()
                val wire = tag and 7
                val payload = if (wire == 2) input.readByteArray() else {
                    check(input.skipField(tag)) { "Unexpected end group" }
                    byteArrayOf()
                }
                add(Field(tag ushr 3, wire, bytes.copyOfRange(start, input.totalBytesRead), payload))
            }
        }
    }.getOrNull()

    private fun encoded(number: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        CodedOutputStream.newInstance(out).apply {
            writeByteArray(number, payload)
            flush()
        }
        return out.toByteArray()
    }

    private fun join(parts: List<ByteArray>): ByteArray = ByteArrayOutputStream().apply {
        parts.forEach { write(it) }
    }.toByteArray()
}
