package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoByteString(
    val value: ByteString
) : ProtoValue, Iterable<Byte> by value {

    override fun toJson(): JsonElement {
        return toByteArray().toHexString().json
    }

    override fun computeSize(tag: Int): Int {
        return CodedOutputStream.computeBytesSize(tag, value)
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        output.writeBytes(tag, value)
    }

    fun toByteArray(): ByteArray {
        return value.toByteArray()
    }

    fun toUtfString(): String {
        return value.toStringUtf8()
    }

    override fun size(): Int = value.size()

    fun isEmpty(): Boolean = value.isEmpty

    fun substring(start: Int, end: Int = value.size()): ProtoByteString {
        return ProtoByteString(value.substring(start, end))
    }

    fun concat(other: ProtoByteString): ProtoByteString {
        return ProtoByteString(value.concat(other.value))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoByteString) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "ByteString(${toByteArray().toHexString()})"

    companion object {
        private fun ByteArray.toHexString(): String {
            val hexChars = "0123456789abcdef"
            val result = CharArray(size * 2)
            for (i in indices) {
                val v = this[i].toInt() and 0xFF
                result[i * 2] = hexChars[v ushr 4]
                result[i * 2 + 1] = hexChars[v and 0x0F]
            }
            return String(result)
        }
    }
}
