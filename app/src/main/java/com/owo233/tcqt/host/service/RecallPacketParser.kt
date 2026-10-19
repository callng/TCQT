package com.owo233.tcqt.host.service

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
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
        val allowedGroupRecalls: Set<Pair<String, Long>> = emptySet(),
    )

    private data class Field(val number: Int, val wire: Int, val raw: ByteArray, val payload: ByteArray)
    private data class Operation(
        val bytes: ByteArray,
        val recalls: List<Recall>,
        val empty: Boolean,
        val allowedGroupRecalls: Set<Pair<String, Long>> = emptySet(),
    )
    private const val GROUP_HEADER_SIZE = 7

    fun msgPush(bytes: ByteArray, currentUid: String, decoder: RecallMetadataDecoder = GoogleRecallMetadataDecoder): Result {
        val fields = fields(bytes) ?: return Result(bytes)
        // A MsgPush has one message. Do not suppress an unexpected multi-message envelope.
        val messages = fields.filter { it.number == 1 && it.wire == 2 }
        if (messages.size != 1) return Result(bytes)
        val message = messages.single()
        val op = operation(message.payload, currentUid, decoder) ?: return Result(bytes)
        if (op.recalls.isEmpty()) return Result(bytes, allowedGroupRecalls = op.allowedGroupRecalls)
        if (op.empty) return Result(bytes, op.recalls, blockPush = true)
        val rewritten = rewriteOperation(message.payload, op.bytes) ?: return Result(bytes)
        return Result(
            join(fields.map { if (it === message) encoded(1, rewritten) else it.raw }), op.recalls,
            allowedGroupRecalls = op.allowedGroupRecalls,
        )
    }

    fun infoSyncPush(
        bytes: ByteArray,
        currentUid: String,
        decoder: RecallMetadataDecoder = GoogleRecallMetadataDecoder,
        isGroupRecalled: (String, Long) -> Boolean = { _, _ -> false },
    ): Result {
        val recalls = mutableListOf<Recall>()
        val allowedGroupRecalls = mutableSetOf<Pair<String, Long>>()
        // 8 = system notifications, 4 = notification body, 8 = QQMessage.
        // push_flag also carries unrelated sync state; inspect the actual messages instead.
        val withoutEvents = rewriteChildren(bytes, 8) { content ->
            rewriteChildren(content, 4) { body ->
                rewriteChildren(body, 8) { message ->
                    val op = operation(message, currentUid, decoder)
                    if (op != null) allowedGroupRecalls.addAll(op.allowedGroupRecalls)
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
        val blockedRecalls = recalls.filterNot { it.group && (it.peerId to it.msgSeq) in allowedGroupRecalls }
        val groupTargets = blockedRecalls.filter { it.group }.mapTo(hashSetOf()) { it.peerId to it.msgSeq }
        // The same sync can carry a recalled message again as a type-127 deletion
        // placeholder in 7 -> 3 -> 6, even outside that group's start/end range.
        // Inspect all records and preserve every unrelated field and sync cursor.
        val rewritten = rewriteChildren(withoutEvents, 7) { content ->
            rewriteChildren(content, 3) { group ->
                val peerId = scalar(group, 3)?.takeIf { it > 0 }?.toString()
                if (peerId == null) group else rewriteChildren(group, 6) { message ->
                    val seq = groupDeletionSequence(message, peerId)
                    if (seq != null && (peerId to seq) !in allowedGroupRecalls &&
                        ((peerId to seq) in groupTargets || isGroupRecalled(peerId, seq))) {
                        null
                    } else message
                }
            }
        }
        return Result(rewritten, blockedRecalls, allowedGroupRecalls = allowedGroupRecalls)
    }

    private fun groupDeletionSequence(message: ByteArray, peerId: String): Long? = runCatching {
        val head = child(message, 1) ?: return null
        val route = child(head, 8) ?: return null
        if (scalar(route, 1)?.toString() != peerId || scalar(route, 2) != 127L) return null
        val content = child(message, 2) ?: return null
        if (scalar(content, 1) != 82L) return null
        scalar(content, 5)?.takeIf { it in 1..0xffffffffL }
    }.getOrNull()

    private fun child(bytes: ByteArray, number: Int): ByteArray? =
        fields(bytes)?.lastOrNull { it.number == number && it.wire == 2 }?.payload

    private fun scalar(bytes: ByteArray, number: Int): Long? = runCatching {
        val field = fields(bytes)?.lastOrNull { it.number == number && it.wire == 0 } ?: return null
        CodedInputStream.newInstance(field.raw).run { readTag(); readUInt64() }
    }.getOrNull()

    private fun operation(bytes: ByteArray, currentUid: String, decoder: RecallMetadataDecoder): Operation? = runCatching {
        val msg = decoder.message(bytes)
        val subtype = if (msg.subtype != 0) msg.subtype else msg.subSeq
        when (msg.type) {
            528 if subtype == 138 -> c2c(msg.operation, msg, currentUid, decoder)
            732 if subtype == 17 -> group(msg.operation, currentUid, decoder)
            else -> null
        }
    }.getOrNull()

    private fun c2c(bytes: ByteArray, msg: RecallMetadataDecoder.Message, currentUid: String, decoder: RecallMetadataDecoder): Operation? {
        val parsed = decoder.c2c(bytes)
        val fields = fields(bytes) ?: return null
        val infos = fields.filter { it.number == 1 && it.wire == 2 }
        if (infos.size != parsed.size) return null
        val recalls = mutableListOf<Recall>()
        val removed = mutableSetOf<Field>()
        infos.zip(parsed).forEach { (field, info) ->
            val operator = info.operatorUid
            val receiver = info.receiverUid
            val peer = when {
                operator.isNotBlank() && operator != currentUid -> operator
                receiver.isNotBlank() && receiver != currentUid -> receiver
                else -> listOf(msg.senderUid, msg.receiverUid)
                    .firstOrNull { it.isNotBlank() && it != currentUid }.orEmpty()
            }
            val seq = info.msgSeq
            if (operator.isNotBlank() && operator != currentUid && peer.isNotBlank() && seq > 0) {
                removed.add(field)
                recalls.add(Recall(
                    false, peer, seq, operator, operator, info.msgId,
                    info.senderMsgSeq, info.msgTime, info.msgRandom,
                ))
            }
        }
        if (recalls.isEmpty()) return null
        return Operation(join(fields.filterNot { it in removed }.map { it.raw }), recalls, removed.size == infos.size)
    }

    private fun group(bytes: ByteArray, currentUid: String, decoder: RecallMetadataDecoder): Operation? {
        // Group operations have a 7-byte binary header, not a protobuf prefix.
        if (bytes.size <= GROUP_HEADER_SIZE) return null
        val op = decoder.group(bytes.copyOfRange(GROUP_HEADER_SIZE, bytes.size))
        val operator = op.operatorUid
        if (op.peerId <= 0 || operator.isBlank()) return null
        val recalls = op.targets.mapNotNull { msg ->
            val seq = msg.msgSeq
            if (seq == 0L) null else Recall(
                true, op.peerId.toString(), seq, operator, msg.senderUid,
                msgTime = msg.msgTime, msgRandom = msg.msgRandom,
            )
        }
        // An incomplete target must not make us drop other operation data we cannot identify.
        if (recalls.isEmpty() || recalls.size != op.targets.size) return null
        if (operator == currentUid) {
            return Operation(
                bytes, emptyList(), empty = false,
                allowedGroupRecalls = recalls.mapTo(hashSetOf()) { it.peerId to it.msgSeq },
            )
        }
        return Operation(bytes, recalls, empty = true)
    }

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
