package com.owo233.tcqt.host.service

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
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

@RunWith(Parameterized::class)
class RecallPacketParserTest(private val useKotlinx: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "kotlinx={0}")
        fun backends(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
    }

    private val decoder = if (useKotlinx) KotlinxRecallMetadataDecoder else GoogleRecallMetadataDecoder
    private fun msgPush(bytes: ByteArray, uid: String) = RecallPacketParser.msgPush(bytes, uid, decoder)
    private fun infoSyncPush(bytes: ByteArray, uid: String) = RecallPacketParser.infoSyncPush(bytes, uid, decoder)

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
        val result = msgPush(packet, self)
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
        val result = msgPush(bytes(1, message(732, 17, group(410, 411))), self)
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
            val result = msgPush(original, self)
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
        val result = infoSyncPush(sync(ordinary, foreign, own) + unknown, self)
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
        val result = infoSyncPush(original, self)
        assertContentEquals(sync(ordinary), result.bytes)
        assertEquals(410L, result.recalls.single().msgSeq)
    }

    @Test
    fun syncInspectsContentWithoutAssumingPushFlagOrSubSeq() {
        val result = infoSyncPush(sync(message(732, 17, group(410), 999), flag = 7), self)
        assertEquals(410L, result.recalls.single().msgSeq)
        assertContentEquals(sync(flag = 7), result.bytes)
        val fallback = msgPush(bytes(1, message(732, 0, group(410), 17)), self)
        assertTrue(fallback.blockPush)
    }

    @Test
    fun realtimeThenRepeatedSyncStillBlocksRecallButCreatesOnlyOneNotice() {
        val message = message(528, 138, c2c())
        val history = RecallEventHistory<RecallManager.MessageKey>(2048)
        val live = msgPush(bytes(1, message), self)
        var notices = 0
        fun notify(recalls: List<RecallPacketParser.Recall>) {
            recalls.forEach {
                if (history.add(RecallManager.MessageKey(self, 1, it.peerId, it.msgSeq))) notices++
            }
        }
        assertTrue(live.blockPush)
        notify(live.recalls)
        repeat(3) {
            val replay = infoSyncPush(sync(message), self)
            assertContentEquals(sync(), replay.bytes) // Filtering must still run on every refresh.
            assertEquals(live.recalls, replay.recalls)
            notify(replay.recalls)
        }
        assertEquals(1, notices)
    }

    @Test
    fun malformedOperationDoesNotPreventOtherSyncRecalls() {
        val malformed = message(732, 17, byteArrayOf(1, 2))
        val result = infoSyncPush(sync(malformed, message(732, 17, group(410))), self)
        assertContentEquals(sync(malformed), result.bytes)
        assertEquals(410L, result.recalls.single().msgSeq)
        val truncated = byteArrayOf(66, 127, 1)
        assertContentEquals(truncated, infoSyncPush(truncated, self).bytes)
    }

    @Test
    fun mixedC2cBatchKeepsOwnRecallAndUnknownOperationData() {
        val own = bytes(1, c2cInfo(35542, self))
        val unknown = num(99, -1)
        val op = c2c() + own + unknown
        val result = msgPush(bytes(1, message(528, 138, op)), self)
        assertFalse(result.blockPush)
        assertEquals(3904L, result.recalls.single().msgSeq)
        assertContentEquals(bytes(1, message(528, 138, own + unknown)), result.bytes)
    }

    @Test
    fun unsignedSequenceIsNotSignExtendedAndMissingSequenceIsNotInvented() {
        val result = msgPush(bytes(1, message(528, 138, c2c(0xffffffffL))), self)
        assertEquals(0xffffffffL, result.recalls.single().msgSeq)
        assertTrue(msgPush(bytes(1, message(528, 138, c2c(0))), self).recalls.isEmpty())
    }

    @Test
    fun invalidUtf8InRecallProfileAndRichTextIsSkippedByMinimalProjection() {
        val invalidText = bytes(1, byteArrayOf(-1, -2))
        val packet = bytes(1,
            bytes(1, text(2, actor) + text(6, self) + bytes(8, invalidText)) +
                bytes(2, num(1, 528) + num(2, 138)) +
                bytes(3, bytes(1, invalidText) + bytes(2, c2c())),
        )
        val result = msgPush(packet, self)
        assertTrue(result.blockPush)
        assertEquals(3904L, result.recalls.single().msgSeq)
    }

    @Test
    fun c2cBatchPreserves64BitMetadataAndAllUnknownEnvelopeFields() {
        val extra = num(99, -1L) + bytes(100, byteArrayOf(-1, -2))
        val op = c2c() + bytes(1, c2cInfo(3905) + num(5, 1792329356) + num(6, 0xffffffffL)) +
            bytes(1, c2cInfo(3906, self)) + extra
        val head = bytes(1, text(2, actor) + text(6, self) + extra)
        val content = bytes(2, num(1, 528) + num(2, 138) + extra)
        val packet = bytes(1, head + content + bytes(3, bytes(2, op) + extra) + extra) + extra
        val result = msgPush(packet, self)
        assertFalse(result.blockPush)
        assertEquals(listOf(3904L, 3905L), result.recalls.map { it.msgSeq })
        assertEquals(1792329356L, result.recalls.last().msgTime)
        assertEquals(0xffffffffL, result.recalls.last().msgRandom)
        val remaining = bytes(1, c2cInfo(3906, self)) + extra
        assertContentEquals(
            bytes(1, head + content + bytes(3, bytes(2, remaining) + extra) + extra) + extra,
            result.bytes,
        )
    }

    @Test
    fun invalidUidOrTruncatedWireCannotCreateFalseRecallOrAbortOtherMessages() {
        val invalidUid = bytes(1, bytes(1, byteArrayOf(-1)) + num(20, 3904))
        val invalid = message(528, 138, invalidUid)
        val own = message(528, 138, c2c(uid = self))
        val valid = message(528, 138, c2c())
        val result = infoSyncPush(sync(invalid, own, valid), self)
        assertEquals(3904L, result.recalls.single().msgSeq)
        assertContentEquals(sync(invalid, own), result.bytes)
        listOf(
            byteArrayOf(10, -1, -1, -1, -1, 15), // Length overflow.
            byteArrayOf(10, 2, 8), // Truncated length-delimited field.
            byteArrayOf(8, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128),
            bytes(1, message(732, 17, ByteArray(7))),
        ).forEach { malformed ->
            val parsed = msgPush(malformed, self)
            assertTrue(parsed.recalls.isEmpty())
            assertFalse(parsed.blockPush)
            assertContentEquals(malformed, parsed.bytes)
        }
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
            val result = if (file.contains("InfoSyncPush")) infoSyncPush(original, uid)
                else msgPush(original, uid)
            if (useKotlinx) {
                val google = if (file.contains("InfoSyncPush")) RecallPacketParser.infoSyncPush(original, uid)
                    else RecallPacketParser.msgPush(original, uid)
                assertEquals(google.recalls, result.recalls, file)
                assertEquals(google.blockPush, result.blockPush, file)
                assertContentEquals(google.bytes, result.bytes, file)
            }
            assertEquals(expected, result.recalls.joinToString(",") { "${it.group}:${it.peerId}:${it.msgSeq}" }, file)
            if (result.recalls.isEmpty()) assertContentEquals(original, result.bytes, file)
            else if (file.contains("MsgPush")) assertTrue(result.blockPush, file)
            else {
                assertFalse(result.blockPush, file)
                assertTrue(infoSyncPush(result.bytes, uid).recalls.isEmpty(), file)
                // Output is still a valid protobuf envelope.
                UnknownFieldSet.parseFrom(result.bytes)
            }
        }
    }
}
