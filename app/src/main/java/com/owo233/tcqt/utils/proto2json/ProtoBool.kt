package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoBool(val value: Boolean) : ProtoValue {

    override fun toJson(): JsonElement = value.json

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return CodedOutputStream.computeBoolSize(tag, value)
    }

    internal fun computeSizeNoTag(): Int = CodedOutputStream.computeBoolSizeNoTag(value)

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        output.writeBool(tag, value)
    }

    internal fun writeNoTag(output: CodedOutputStream) {
        output.writeBoolNoTag(value)
    }

    fun toLong(): Long = if (value) 1L else 0L
    fun toInt(): Int = if (value) 1 else 0

    override fun equals(other: Any?): Boolean = other is ProtoBool && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}
