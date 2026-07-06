package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoList(
    value: Collection<ProtoValue> = emptyList()
) : ProtoValue, Iterable<ProtoValue> {

    val value: MutableList<ProtoValue> = value.toMutableList()

    override fun iterator(): Iterator<ProtoValue> = value.iterator()

    override fun toJson(): JsonElement = value.map(ProtoValue::toJson).jsonArray

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return value.sumOf { it.computeSize(tag) }
    }

    override fun add(v: ProtoValue) {
        value.add(v)
    }

    fun add(v: Number) = add(v.proto)
    fun add(v: UInt) = add(protoUInt32Of(v))
    fun add(v: ULong) = add(protoUInt64Of(v))
    fun add(v: String) = add(v.proto)
    fun add(v: ByteArray) = add(v.proto)
    fun add(v: ByteString) = add(v.proto)
    fun add(v: Boolean) = add(v.proto)
    fun addAny(v: Any?) = add(ProtoUtils.any2proto(v))

    override fun remove(tag: Int): Boolean {
        if (tag !in value.indices) return false
        value.removeAt(tag)
        return true
    }

    fun removeAt(index: Int): ProtoValue = value.removeAt(index)
    fun remove(v: ProtoValue): Boolean = value.remove(v)

    override operator fun set(tag: Int, v: ProtoValue) {
        value[tag] = v
    }

    override operator fun set(tag: Int, v: Number) {
        value[tag] = v.proto
    }

    operator fun set(index: Int, v: String) {
        value[index] = v.proto
    }

    operator fun set(index: Int, v: Boolean) {
        value[index] = v.proto
    }

    fun setAt(index: Int, v: ProtoValue) {
        this[index] = v
    }

    fun setAt(index: Int, v: Number) {
        this[index] = v
    }

    override fun size(): Int = value.size
    operator fun get(index: Int): ProtoValue = value[index]
    fun getOrNull(index: Int): ProtoValue? = value.getOrNull(index)
    fun isEmpty(): Boolean = value.isEmpty()
    fun isNotEmpty(): Boolean = value.isNotEmpty()
    fun contains(v: ProtoValue): Boolean = value.contains(v)
    fun indexOf(v: ProtoValue): Int = value.indexOf(v)
    fun lastIndexOf(v: ProtoValue): Int = value.lastIndexOf(v)
    fun clear() = value.clear()

    fun subList(fromIndex: Int, toIndex: Int): ProtoList =
        ProtoList(ArrayList(value.subList(fromIndex, toIndex)))

    fun packed(type: ProtoPackedType = ProtoPackedType.infer(value)): ProtoPacked =
        ProtoPacked(type, this)

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        value.forEach { it.writeTo(output, tag) }
    }

    override fun deepCopy(): ProtoList = ProtoList(value.mapTo(arrayListOf()) { it.deepCopy() })

    override fun equals(other: Any?): Boolean = other is ProtoList && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = toJson().toString()
}
