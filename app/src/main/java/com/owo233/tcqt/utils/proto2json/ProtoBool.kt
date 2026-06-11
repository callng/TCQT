package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoBool(
    val value: Boolean
) : ProtoValue {

    override fun toJson(): JsonElement {
        return value.json
    }

    override fun computeSize(tag: Int): Int {
        return CodedOutputStream.computeBoolSize(tag, value)
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        output.writeBool(tag, value)
    }

    fun toLong(): Long = if (value) 1L else 0L

    fun toInt(): Int = if (value) 1 else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoBool) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()
}
