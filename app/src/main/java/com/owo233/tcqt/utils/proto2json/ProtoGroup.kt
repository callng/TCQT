package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import kotlinx.serialization.json.JsonElement

/** Preserves the deprecated protobuf group wire type (START_GROUP / END_GROUP). */
class ProtoGroup(val value: ProtoMap = ProtoMap()) : ProtoValue {

    override fun toJson(): JsonElement = value.toJson()

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return CodedOutputStream.computeTagSize(tag) * 2 + value.computeSizeDirectly()
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        output.writeTag(tag, WireFormat.WIRETYPE_START_GROUP)
        value.writeFieldsTo(output)
        output.writeTag(tag, WireFormat.WIRETYPE_END_GROUP)
    }

    override fun deepCopy(): ProtoGroup = ProtoGroup(value.deepCopy())

    override fun equals(other: Any?): Boolean = other is ProtoGroup && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}
