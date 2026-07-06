package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject

class ProtoMap(
    value: Map<Int, ProtoValue> = emptyMap()
) : ProtoValue {

    val value: MutableMap<Int, ProtoValue> = LinkedHashMap(value)

    init {
        this.value.keys.forEach(ProtoUtils::requireValidTag)
    }

    override fun has(vararg tags: Int): Boolean = getOrNull(*tags) != null

    override operator fun contains(tag: Int): Boolean {
        ProtoUtils.requireValidTag(tag)
        return value.containsKey(tag)
    }

    override operator fun set(tag: Int, v: ProtoValue) {
        ProtoUtils.requireValidTag(tag)
        value[tag] = v
    }

    override operator fun set(tag: Int, v: Number) {
        this[tag] = v.proto
    }

    fun append(tag: Int, v: ProtoValue) {
        ProtoUtils.requireValidTag(tag)
        when (val oldValue = value[tag]) {
            null -> value[tag] = v
            is ProtoList -> oldValue.add(v)
            else -> value[tag] = ProtoList(arrayListOf(oldValue, v))
        }
    }

    fun append(tag: Int, v: Number) = append(tag, v.proto)
    fun append(tag: Int, v: String) = append(tag, v.proto)
    fun append(tag: Int, v: Boolean) = append(tag, v.proto)
    fun append(tag: Int, v: ByteArray) = append(tag, v.proto)
    fun append(tag: Int, v: ByteString) = append(tag, v.proto)

    override operator fun get(vararg tags: Int): ProtoValue = getOrNull(*tags)
        ?: throw NoSuchElementException("Tag path ${tags.contentToString()} not found")

    fun getOrNull(vararg tags: Int): ProtoValue? {
        if (tags.isEmpty()) return null
        var current: ProtoMap = this
        tags.forEachIndexed { index, tag ->
            ProtoUtils.requireValidTag(tag)
            val field = current.value[tag] ?: return null
            if (index == tags.lastIndex) return field
            current = field.containerMapOrNull() ?: return null
        }
        return null
    }

    override fun remove(tag: Int): Boolean {
        ProtoUtils.requireValidTag(tag)
        return value.remove(tag) != null
    }

    fun remove(vararg tags: Int): Boolean {
        if (tags.isEmpty()) return false
        if (tags.size == 1) return remove(tags[0])

        var current = this
        for (index in 0 until tags.lastIndex) {
            ProtoUtils.requireValidTag(tags[index])
            current = current.value[tags[index]]?.containerMapOrNull() ?: return false
        }
        return current.remove(tags.last())
    }

    override fun size(): Int = value.size
    fun isEmpty(): Boolean = value.isEmpty()
    fun isNotEmpty(): Boolean = value.isNotEmpty()
    fun clear() = value.clear()

    val keys: Set<Int> get() = value.keys
    val entries: Set<Map.Entry<Int, ProtoValue>> get() = value.entries
    val values: Collection<ProtoValue> get() = value.values

    operator fun set(vararg tags: Int, v: ProtoValue) {
        require(tags.isNotEmpty()) { "Tag path cannot be empty" }
        tags.forEach(ProtoUtils::requireValidTag)

        var current = this
        for (index in 0 until tags.lastIndex) {
            val tag = tags[index]
            val existing = current.value[tag]
            current = when (existing) {
                null -> ProtoMap().also { current[tag] = it }
                is ProtoMap -> existing
                is ProtoGroup -> existing.value
                else -> throw IllegalStateException(
                    "Tag $tag in path ${tags.contentToString()} is ${existing::class.simpleName}, not a message"
                )
            }
        }
        current[tags.last()] = v
    }

    operator fun set(vararg tags: Int, struct: ProtoMap.() -> Unit) {
        set(*tags, v = ProtoMap().apply(struct))
    }

    operator fun set(vararg tags: Int, v: String) = set(*tags, v = v.proto)
    operator fun set(vararg tags: Int, v: ByteArray) = set(*tags, v = v.proto)
    operator fun set(vararg tags: Int, v: Number) = set(*tags, v = v.proto)
    operator fun set(vararg tags: Int, v: UInt) = set(*tags, v = protoUInt32Of(v))
    operator fun set(vararg tags: Int, v: ULong) = set(*tags, v = protoUInt64Of(v))
    operator fun set(vararg tags: Int, v: Boolean) = set(*tags, v = v.proto)
    operator fun set(vararg tags: Int, v: ByteString) = set(*tags, v = v.proto)
    operator fun set(vararg tags: Int, v: Any) = set(*tags, v = ProtoUtils.any2proto(v))

    override fun toJson(): JsonElement = buildJsonObject {
        value.toSortedMap().forEach { (tag, field) ->
            put(tag.toString(), field.toJson())
        }
    }

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        val dataSize = computeSizeDirectly()
        return CodedOutputStream.computeTagSize(tag) +
            CodedOutputStream.computeUInt32SizeNoTag(dataSize) + dataSize
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        output.writeTag(tag, WireFormat.WIRETYPE_LENGTH_DELIMITED)
        output.writeUInt32NoTag(computeSizeDirectly())
        writeFieldsTo(output)
    }

    override fun computeSizeDirectly(): Int = value.entries.sumOf { (tag, proto) ->
        proto.computeSize(tag)
    }

    internal fun writeFieldsTo(output: CodedOutputStream) {
        value.forEach { (tag, proto) -> proto.writeTo(output, tag) }
    }

    fun toByteArray(): ByteArray = ProtoUtils.encodeToByteArray(this)

    override fun deepCopy(): ProtoMap = ProtoMap(
        value.mapValuesTo(linkedMapOf()) { (_, field) -> field.deepCopy() }
    )

    override fun equals(other: Any?): Boolean = other is ProtoMap && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = toJson().toString()

    private fun ProtoValue.containerMapOrNull(): ProtoMap? = when (this) {
        is ProtoMap -> this
        is ProtoGroup -> value
        else -> null
    }
}
