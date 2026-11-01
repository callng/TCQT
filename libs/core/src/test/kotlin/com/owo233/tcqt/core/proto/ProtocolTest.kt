package com.owo233.tcqt.core.proto

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import kotlin.test.*

class ProtocolTest {
    @Test fun wireTypesAndOpaqueBytesRoundTrip() {
        val stream = ByteArrayOutputStream()
        CodedOutputStream.newInstance(stream).apply {
            writeUInt64(1, 17); writeUInt64(1, 99)
            writeFixed64(2, Long.MIN_VALUE)
            writeByteArray(3, byteArrayOf(-1, 0, -128))
            writeFixed32(4, Int.MIN_VALUE)
            flush()
        }
        val wire = stream.toByteArray()
        val decoded = ProtoUtils.decodeFromByteArray(wire, ProtoDecodeMode.WIRE_PRESERVING)
        assertContentEquals(byteArrayOf(-1, 0, -128), decoded[3].asByteArray)
        assertContentEquals(wire, ProtoUtils.encodeToByteArray(decoded))
    }
    @Test fun packedSignedValuesRequireAnExplicitSchema() {
        val packed = protoPackedOf(ProtoPackedType.SINT32, protoSInt32Of(-7), protoSInt32Of(0), protoSInt32Of(Int.MAX_VALUE))
        val wire = ProtoUtils.encodeSingleField(packed, 1)
        val bytes = ProtoUtils.decodeFromByteArray(wire, ProtoDecodeMode.WIRE_PRESERVING)[1] as ProtoByteString
        assertEquals(listOf(-7, 0, Int.MAX_VALUE), ProtoUtils.decodePacked(bytes, ProtoPackedType.SINT32).map { it.asInt })
    }
    @Test fun compatibleModeResolvesNestedMessages() {
        val wire = ProtoUtils.encodeToByteArray(protobufOf((1 to 2) to 42))
        assertEquals(42, ProtoUtils.decodeFromByteArray(wire)[1, 2].asInt)
        assertIs<ProtoByteString>(ProtoUtils.decodeFromByteArray(wire, ProtoDecodeMode.WIRE_PRESERVING)[1])
    }
    @Test fun rejectsInvalidTagsAndTruncatedPayloads() {
        assertFailsWith<IllegalArgumentException> { protobufOf(0 to 1) }
        assertFailsWith<IllegalArgumentException> { ProtoUtils.encodeSingleField(protoIntOf(1), -1) }
        assertFails { ProtoUtils.decodeFromByteArray(byteArrayOf(10, 5, -1)) }
    }
    @Test fun rejectsMixedPackedTypes() {
        assertFailsWith<IllegalArgumentException> { ProtoPacked(listOf(protoBoolOf(true), protoIntOf(1))) }
    }
}
