package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoList(
    val value: ArrayList<ProtoValue> = arrayListOf()
) : ProtoValue, Iterable<ProtoValue> by value {

    override fun toJson(): JsonElement {
        val array = arrayListOf<JsonElement>()
        value.forEach {
            array.add(it.toJson())
        }
        return array.jsonArray
    }

    override fun computeSize(tag: Int): Int {
        var size = 0
        value.forEach {
            size += it.computeSize(tag)
        }
        return size
    }

    override fun add(v: ProtoValue) {
        value.add(v)
    }

    fun add(v: Number) {
        value.add(v.proto)
    }

    fun add(v: String) {
        value.add(v.proto)
    }

    fun add(v: ByteArray) {
        value.add(v.proto)
    }

    fun add(v: Boolean) {
        value.add(v.proto)
    }

    override fun remove(tag: Int): Boolean {
        if (tag < 0 || tag >= value.size) return false
        value.removeAt(tag)
        return true
    }

    fun removeAt(index: Int): ProtoValue {
        return value.removeAt(index)
    }

    fun setAt(index: Int, v: ProtoValue) {
        value[index] = v
    }

    fun setAt(index: Int, v: Number) {
        value[index] = v.proto
    }

    override fun size(): Int {
        return value.size
    }

    operator fun get(index: Int): ProtoValue {
        return value[index]
    }

    fun isEmpty(): Boolean = value.isEmpty()

    fun isNotEmpty(): Boolean = value.isNotEmpty()

    fun contains(v: ProtoValue): Boolean = value.contains(v)

    fun indexOf(v: ProtoValue): Int = value.indexOf(v)

    fun lastIndexOf(v: ProtoValue): Int = value.lastIndexOf(v)

    fun subList(fromIndex: Int, toIndex: Int): ProtoList {
        return ProtoList(ArrayList(value.subList(fromIndex, toIndex)))
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        value.forEach {
            it.writeTo(output, tag)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoList) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return toJson().toString()
    }

    fun deepCopy(): ProtoList {
        val copy = ProtoList()
        value.forEach { v ->
            copy.add(when (v) {
                is ProtoMap -> v.deepCopy()
                is ProtoList -> v.deepCopy()
                else -> v
            })
        }
        return copy
    }
}
