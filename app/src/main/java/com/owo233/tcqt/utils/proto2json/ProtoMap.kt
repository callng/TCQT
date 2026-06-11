package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import kotlinx.serialization.json.JsonElement

class ProtoMap(
    val value: HashMap<Int, ProtoValue> = hashMapOf()
) : ProtoValue {

    override fun has(vararg tags: Int): Boolean {
        var curMap: ProtoMap = this
        tags.forEachIndexed { index, tag ->
            if (tag !in curMap) {
                return false
            }
            if (index == tags.size - 1) {
                return true
            }
            val next = curMap[tag]
            if (next is ProtoMap) {
                curMap = next
            } else {
                return false
            }
        }
        return true
    }

    override fun contains(tag: Int): Boolean {
        return value.containsKey(tag)
    }

    override operator fun set(tag: Int, v: ProtoValue) {
        if (!contains(tag)) {
            value[tag] = v
        } else {
            val oldValue = value[tag]!!
            if (oldValue is ProtoList) {
                oldValue.add(v)
            } else {
                value[tag] = ProtoList(arrayListOf(oldValue, v))
            }
        }
    }

    override operator fun set(tag: Int, v: Number) {
        if (!contains(tag)) {
            value[tag] = ProtoNumber(v)
        } else {
            val oldValue = value[tag]!!
            if (oldValue is ProtoList) {
                oldValue.add(v.proto)
            } else {
                value[tag] = ProtoList(arrayListOf(oldValue, v.proto))
            }
        }
    }

    override operator fun get(vararg tags: Int): ProtoValue {
        var curMap = value
        tags.forEachIndexed { index, tag ->
            val v = curMap[tag] ?: error("Tag $tag not found")
            if (index == tags.size - 1) {
                return v
            }
            if (v is ProtoMap) {
                curMap = v.value
            } else {
                return v
            }
        }
        error("Instance is not ProtoMap")
    }

    override fun remove(tag: Int): Boolean {
        return value.remove(tag) != null
    }

    fun remove(vararg tags: Int): Boolean {
        if (tags.isEmpty()) return false
        if (tags.size == 1) return remove(tags[0])

        var curMap: ProtoMap = this
        for (i in 0 until tags.size - 1) {
            val next = curMap.value[tags[i]]
            if (next !is ProtoMap) return false
            curMap = next
        }
        return curMap.value.remove(tags.last()) != null
    }

    override fun size(): Int {
        return value.size
    }

    val keys: Set<Int> get() = value.keys

    val entries: Set<Map.Entry<Int, ProtoValue>> get() = value.entries

    val values: Collection<ProtoValue> get() = value.values

    operator fun set(vararg tags: Int, v: ProtoValue) {
        var curProtoMap: ProtoMap = this
        tags.forEachIndexed { index, tag ->
            if (index == tags.size - 1) {
                return@forEachIndexed
            }
            if (!curProtoMap.contains(tag)) {
                val tmp = ProtoMap()
                curProtoMap[tag] = tmp
                curProtoMap = tmp
            } else {
                curProtoMap = curProtoMap[tag].asMap
            }
        }
        curProtoMap[tags.last()] = v
    }

    operator fun set(vararg tags: Int, struct: (ProtoMap) -> Unit) {
        val map = ProtoMap()
        struct.invoke(map)
        set(*tags, v = map)
    }

    operator fun set(vararg tags: Int, v: String) {
        set(*tags, v = v.proto)
    }

    operator fun set(vararg tags: Int, v: ByteArray) {
        set(*tags, v = v.proto)
    }

    operator fun set(vararg tags: Int, v: Number) {
        set(*tags, v = v.proto)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun set(vararg tags: Int, v: UInt) {
        set(*tags, v = ProtoNumber(v.toInt(), isUnsigned = true))
    }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun set(vararg tags: Int, v: ULong) {
        set(*tags, v = ProtoNumber(v.toLong(), isUnsigned = true))
    }

    operator fun set(vararg tags: Int, v: Boolean) {
        set(*tags, v = v.proto)
    }

    operator fun set(vararg tags: Int, v: ByteString) {
        set(*tags, v = v.proto)
    }

    operator fun set(vararg tags: Int, v: Any) {
        set(*tags, v = ProtoUtils.any2proto(v))
    }

    override fun toJson(): JsonElement {
        val hashMap = hashMapOf<String, JsonElement>()
        value.forEach { (tag, field) ->
            hashMap[tag.toString()] = field.toJson()
        }
        return hashMap.jsonObject
    }

    override fun computeSize(tag: Int): Int {
        val size = CodedOutputStream.computeTagSize(tag)
        val dataSize = computeSizeDirectly()
        return size + ProtoUtils.computeRawVarint32Size(dataSize) + dataSize
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        output.writeTag(tag, WireFormat.WIRETYPE_LENGTH_DELIMITED)
        val dataSize = computeSizeDirectly()
        output.writeUInt32NoTag(dataSize)
        value.forEach { (tag, proto) ->
            proto.writeTo(output, tag)
        }
    }

    override fun computeSizeDirectly(): Int {
        var size = 0
        value.forEach { (tag, proto) ->
            size += proto.computeSize(tag)
        }
        return size
    }

    override fun toString(): String {
        return toJson().toString()
    }

    fun toByteArray(): ByteArray {
        return ProtoUtils.encodeToByteArray(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoMap) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    fun deepCopy(): ProtoMap {
        val copy = ProtoMap()
        value.forEach { (tag, v) ->
            copy[tag] = when (v) {
                is ProtoMap -> v.deepCopy()
                is ProtoList -> v.deepCopy()
                else -> v
            }
        }
        return copy
    }
}
