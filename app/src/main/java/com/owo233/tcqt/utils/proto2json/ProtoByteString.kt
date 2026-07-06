package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import java.util.Base64
import kotlinx.serialization.json.JsonElement

class ProtoByteString(val value: ByteString) : ProtoValue, Iterable<Byte> by value {

    /** Official ProtoJSON representation for bytes is padded standard Base64. */
    override fun toJson(): JsonElement = Base64.getEncoder().encodeToString(toByteArray()).json

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return CodedOutputStream.computeBytesSize(tag, value)
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        output.writeBytes(tag, value)
    }

    fun toByteArray(): ByteArray = value.toByteArray()
    fun toUtfString(): String = value.toStringUtf8()
    fun isValidUtf8(): Boolean = value.isValidUtf8
    override fun size(): Int = value.size()
    fun isEmpty(): Boolean = value.isEmpty

    fun substring(start: Int, end: Int = value.size()): ProtoByteString =
        ProtoByteString(value.substring(start, end))

    fun concat(other: ProtoByteString): ProtoByteString =
        ProtoByteString(value.concat(other.value))

    fun toHexString(): String = value.toByteArray().toHexString()

    @JvmOverloads
    fun decodeAsMessage(
        mode: ProtoDecodeMode = ProtoDecodeMode.COMPATIBLE
    ): ProtoMap = ProtoUtils.decodeFromByteString(value, mode)

    fun decodeAsPacked(type: ProtoPackedType): ProtoPacked = ProtoPacked.decode(value, type)

    override fun equals(other: Any?): Boolean = other is ProtoByteString && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "ByteString(${toHexString()})"
}
