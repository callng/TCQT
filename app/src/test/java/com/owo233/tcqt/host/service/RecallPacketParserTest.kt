package com.owo233.tcqt.host.service

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.artmoe.inao.entries.InfoSyncPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

class RecallPacketParserTest {
    private val self = "u_self"
    private val actor = "u_actor"

    private fun bytes(tag: Int, payload: ByteArray): ByteArray = encode { writeByteArray(tag, payload) }
    private fun text(tag: Int, value: String) = bytes(tag, value.toByteArray())
    private fun num(tag: Int, value: Long) = encode { writeUInt64(tag, value) }
    private fun encode(block: CodedOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().let { out ->
        CodedOutputStream.newInstance(out).apply { block(); flush() }
        out.toByteArray()
    }

    private fun c2cInfo(seq: Long, uid: String = actor) =
        text(1, uid) + text(2, self) + num(3, 35541) + num(4, 72057594610386649) + num(20, seq)

    private fun c2c(seq: Long = 3904, uid: String = actor) = bytes(1, c2cInfo(seq, uid))

    private fun group(vararg seqs: Long, uid: String = actor): ByteArray {
        val info = text(1, uid) + seqs.fold(byteArrayOf()) { all, seq ->
            all + bytes(3, num(1, seq) + text(6, actor))
        }
        return byteArrayOf(0, 1, 2, 3, 17, 0, 0) + num(4, 123456) + bytes(11, info) + num(37, 413)
    }

    private fun message(type: Long, subtype: Long, op: ByteArray, subSeq: Long = subtype): ByteArray =
        bytes(1, text(2, actor) + text(6, self)) +
            bytes(2, num(1, type) + num(2, subtype) + num(3, subSeq)) +
            bytes(3, bytes(2, op))

    private fun sync(vararg messages: ByteArray, flag: Long = 2): ByteArray =
        num(3, flag) + bytes(8, bytes(4, messages.fold(text(2, actor)) { all, msg -> all + bytes(8, msg) }))

    @Test
    fun c2cRestoresField20AndRetainsSenderSequenceAndMessageIdForLookup() {
        val packet = bytes(1, message(528, 138, c2c()))
        val result = RecallPacketParser.msgPush(packet, self)
        assertTrue(result.blockPush)
        assertEquals(3904L, result.recalls.single().msgSeq)
        assertEquals(35541L, result.recalls.single().senderMsgSeq)
        assertEquals(72057594610386649L, result.recalls.single().msgId)
        assertEquals(actor, result.recalls.single().peerId)
        val legacy = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(c2c())
        assertEquals(3904, legacy.info.msgSeq)
        assertEquals(35541, legacy.info.senderMsgSeq)
    }

    @Test
    fun groupUsesTargetSequenceAndIncludesEveryBatchTarget() {
        val result = RecallPacketParser.msgPush(bytes(1, message(732, 17, group(410, 411))), self)
        assertTrue(result.blockPush)
        assertEquals(listOf(410L, 411L), result.recalls.map { it.msgSeq })
        assertEquals(listOf("123456", "123456"), result.recalls.map { it.peerId })
    }

    @Test
    fun ownRecallAndUnknownMessageRemainByteIdentical() {
        listOf(
            bytes(1, message(732, 17, group(410, uid = self))),
            bytes(1, message(528, 138, c2c(uid = self))),
            bytes(1, message(528, 277, c2c())),
            bytes(1, message(732, 20, group(410))),
        ).forEach { original ->
            val result = RecallPacketParser.msgPush(original, self)
            assertContentEquals(original, result.bytes)
            assertTrue(result.recalls.isEmpty())
            assertFalse(result.blockPush)
        }
    }

    @Test
    fun syncRetainsUnknownFieldsAndOtherMessagesExactly() {
        val ordinary = message(82, 0, byteArrayOf())
        val own = message(732, 17, group(410, uid = self))
        val foreign = message(732, 17, group(411))
        val unknown = num(99, -1L) + bytes(100, byteArrayOf(-1, -2, -3))
        val result = RecallPacketParser.infoSyncPush(sync(ordinary, foreign, own) + unknown, self)
        assertContentEquals(sync(ordinary, own) + unknown, result.bytes)
        assertEquals(411L, result.recalls.single().msgSeq)
    }

    @Test
    fun invalidUtf8InUnrelatedRichTextDoesNotDisableRecallFiltering() {
        val richText = bytes(1, bytes(2, bytes(1, bytes(1, byteArrayOf(-1)))))
        val ordinary = message(82, 0, byteArrayOf()) + bytes(3, richText)
        val original = sync(ordinary, message(732, 17, group(410)))
        // Reproduces the old whole-tree schema's strict UTF-8 failure.
        kotlin.test.assertFailsWith<com.google.protobuf.InvalidProtocolBufferException> {
            InfoSyncPushOuterClass.InfoSyncPush.parseFrom(original)
        }
        val result = RecallPacketParser.infoSyncPush(original, self)
        assertContentEquals(sync(ordinary), result.bytes)
        assertEquals(410L, result.recalls.single().msgSeq)
    }

    @Test
    fun syncInspectsContentWithoutAssumingPushFlagOrSubSeq() {
        val result = RecallPacketParser.infoSyncPush(sync(message(732, 17, group(410), 999), flag = 7), self)
        assertEquals(410L, result.recalls.single().msgSeq)
        assertContentEquals(sync(flag = 7), result.bytes)
        val fallback = RecallPacketParser.msgPush(bytes(1, message(732, 0, group(410), 17)), self)
        assertTrue(fallback.blockPush)
    }

    @Test
    fun realtimeThenRepeatedSyncStillBlocksRecallButCreatesOnlyOneNotice() {
        val message = message(528, 138, c2c())
        val history = RecallEventHistory<RecallManager.MessageKey>(2048)
        val live = RecallPacketParser.msgPush(bytes(1, message), self)
        var notices = 0
        fun notify(recalls: List<RecallPacketParser.Recall>) {
            recalls.forEach {
                if (history.add(RecallManager.MessageKey(self, 1, it.peerId, it.msgSeq))) notices++
            }
        }
        assertTrue(live.blockPush)
        notify(live.recalls)
        repeat(3) {
            val replay = RecallPacketParser.infoSyncPush(sync(message), self)
            assertContentEquals(sync(), replay.bytes) // Filtering must still run on every refresh.
            assertEquals(live.recalls, replay.recalls)
            notify(replay.recalls)
        }
        assertEquals(1, notices)
    }

    @Test
    fun malformedOperationDoesNotPreventOtherSyncRecalls() {
        val malformed = message(732, 17, byteArrayOf(1, 2))
        val result = RecallPacketParser.infoSyncPush(sync(malformed, message(732, 17, group(410))), self)
        assertContentEquals(sync(malformed), result.bytes)
        assertEquals(410L, result.recalls.single().msgSeq)
        val truncated = byteArrayOf(66, 127, 1)
        assertContentEquals(truncated, RecallPacketParser.infoSyncPush(truncated, self).bytes)
    }

    @Test
    fun mixedC2cBatchKeepsOwnRecallAndUnknownOperationData() {
        val own = bytes(1, c2cInfo(35542, self))
        val unknown = num(99, -1)
        val op = c2c() + own + unknown
        val result = RecallPacketParser.msgPush(bytes(1, message(528, 138, op)), self)
        assertFalse(result.blockPush)
        assertEquals(3904L, result.recalls.single().msgSeq)
        assertContentEquals(bytes(1, message(528, 138, own + unknown)), result.bytes)
    }

    @Test
    fun unsignedSequenceIsNotSignExtendedAndMissingSequenceIsNotInvented() {
        val result = RecallPacketParser.msgPush(bytes(1, message(528, 138, c2c(0xffffffffL))), self)
        assertEquals(0xffffffffL, result.recalls.single().msgSeq)
        assertTrue(RecallPacketParser.msgPush(bytes(1, message(528, 138, c2c(0))), self).recalls.isEmpty())
    }

    @Test
    fun replayLocalGtxCaptureWhenAvailable() {
        // Private packet bytes stay in ignored local storage, never in the test fixtures.
        val root = System.getenv("TCQT_RECALL_REPLAY_DIR")?.let(Path::of)
        assumeTrue("Set TCQT_RECALL_REPLAY_DIR to replay local capture", root != null)
        val manifest = Files.readAllLines(root!!.resolve("manifest.tsv"))
        manifest.forEach { row ->
            val (file, uid, expected) = row.split('\t')
            val original = Files.readAllBytes(root.resolve(file))
            val result = if (file.contains("InfoSyncPush")) RecallPacketParser.infoSyncPush(original, uid)
                else RecallPacketParser.msgPush(original, uid)
            assertEquals(expected, result.recalls.joinToString(",") { "${it.group}:${it.peerId}:${it.msgSeq}" }, file)
            if (result.recalls.isEmpty()) assertContentEquals(original, result.bytes, file)
            else if (file.contains("MsgPush")) assertTrue(result.blockPush, file)
            else {
                assertFalse(result.blockPush, file)
                assertTrue(RecallPacketParser.infoSyncPush(result.bytes, uid).recalls.isEmpty(), file)
                // Output is still a valid protobuf envelope.
                UnknownFieldSet.parseFrom(result.bytes)
            }
        }
    }
}
