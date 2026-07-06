package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import java.nio.charset.StandardCharsets
import java.util.stream.IntStream
import kotlinx.serialization.json.JsonElement

/** A schema-known protobuf string. Schema-free textual LEN fields decode as ProtoByteString. */
class ProtoString(
    val value: String
) : ProtoValue, CharSequence by value {

    init {
        require(StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            "Protobuf string contains an unpaired UTF-16 surrogate"
        }
    }

    override fun chars(): IntStream = value.chars()

    override fun codePoints(): IntStream = value.codePoints()

    override fun toJson(): JsonElement = value.json

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return CodedOutputStream.computeStringSize(tag, value)
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        output.writeString(tag, value)
    }

    fun toByteString(): ByteString = ByteString.copyFromUtf8(value)

    fun toByteArray(): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

    override fun equals(other: Any?): Boolean =
        other is ProtoString && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}
