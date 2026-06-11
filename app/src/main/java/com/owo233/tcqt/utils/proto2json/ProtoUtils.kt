package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import com.google.protobuf.UnknownFieldSet

object ProtoUtils {

    fun decodeFromByteArray(data: ByteArray): ProtoMap {
        val unknownFieldSet = UnknownFieldSet.parseFrom(data)
        val dest = ProtoMap()
        convertUnknownFieldSet(unknownFieldSet, dest)
        return dest
    }

    fun encodeToByteArray(protoMap: ProtoMap): ByteArray {
        val size = protoMap.computeSizeDirectly()
        val dest = ByteArray(size)
        val output = CodedOutputStream.newInstance(dest)
        protoMap.value.forEach { (tag, proto) ->
            proto.writeTo(output, tag)
        }
        output.checkNoSpaceLeft()
        return dest
    }

    internal fun computeRawVarint32Size(size: Int): Int {
        if (size and -128 == 0) return 1
        if (size and -16384 == 0) return 2
        if (-2097152 and size == 0) return 3
        return if (size and -268435456 == 0) 4 else 5
    }

    internal fun any2proto(any: Any): ProtoValue {
        return when (any) {
            is ProtoValue -> any
            is Boolean -> ProtoBool(any)
            is Number -> any.proto
            is ByteArray -> any.proto
            is String -> any.proto
            is ByteString -> any.proto
            is Array<*> -> ProtoList(arrayListOf(*any.map { any2proto(it!!) }.toTypedArray()))
            is Collection<*> -> ProtoList(arrayListOf(*any.map { any2proto(it!!) }.toTypedArray()))
            is Map<*, *> -> ProtoMap(hashMapOf(*any.map { (k, v) ->
                k as Int to any2proto(v!!)
            }.toTypedArray()))

            is Pair<*, *> -> {
                val (tag, v) = any
                val value = any2proto(v!!)
                when (tag) {
                    is Pair<*, *> -> ProtoMap().apply {
                        val tags = walkPairTags(tag)
                        set(*tags.toIntArray(), v = value)
                    }

                    is Number -> ProtoMap(hashMapOf(tag.toInt() to value))
                    else -> error("Not support type for tag: ${tag.toString()}")
                }
            }

            else -> error("Not support type: ${any::class.simpleName}")
        }
    }

    fun walkPairTags(pair: Pair<*, *>, tags: MutableList<Int> = mutableListOf()): List<Int> {
        val (k, v) = pair
        if (k is Number) {
            tags.add(k.toInt())
        } else {
            walkPairTags(k as Pair<*, *>, tags)
        }
        tags.add(v as Int)
        return tags
    }

    private fun convertUnknownFieldSet(set: UnknownFieldSet, dest: ProtoMap) {
        set.asMap().forEach { (tag, field) ->
            // varint fields (wire type 0): int32, int64, uint32, uint64, sint32, sint64, bool, enum
            field.varintList.forEach { value ->
                dest[tag] = value
            }
            // fixed32 fields (wire type 5): fixed32, sfixed32, float
            field.fixed32List.forEach { value ->
                dest[tag] = value
            }
            // fixed64 fields (wire type 1): fixed64, sfixed64, double
            field.fixed64List.forEach { value ->
                dest[tag] = value
            }
            // length-delimited fields (wire type 2): string, bytes, embedded messages, packed repeated
            field.lengthDelimitedList.forEach { bs ->
                // Try to parse as embedded message first
                try {
                    val nestedSet = UnknownFieldSet.parseFrom(bs)
                    val nestedMap = ProtoMap()
                    convertUnknownFieldSet(nestedSet, nestedMap)
                    dest[tag] = nestedMap
                } catch (_: Throwable) {
                    // Not a valid message — try to decode as packed repeated field
                    val packedList = tryDecodePackedField(bs)
                    if (!packedList.isNullOrEmpty()) {
                        packedList.forEach { dest[tag] = it }
                    } else {
                        // Store as raw bytes
                        dest[tag] = ProtoByteString(bs)
                    }
                }
            }
            // group fields (wire type 3/4): groups (deprecated, rarely used)
            field.groupList.forEach { groupSet ->
                val groupMap = ProtoMap()
                convertUnknownFieldSet(groupSet, groupMap)
                dest[tag] = groupMap
            }
        }
    }

    /**
     * Attempts to decode a length-delimited blob as a packed repeated field.
     * Returns null if the blob is not a valid packed field.
     */
    private fun tryDecodePackedField(data: ByteString): List<ProtoValue>? {
        if (data.size() == 0) return null
        val input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer())
        val values = mutableListOf<ProtoValue>()
        try {
            while (!input.isAtEnd) {
                val tag = input.readTag()
                val wireType = WireFormat.getTagWireType(tag)
                val value = when (wireType) {
                    WireFormat.WIRETYPE_VARINT -> ProtoNumber(input.readUInt64())
                    WireFormat.WIRETYPE_FIXED64 -> ProtoNumber(input.readDouble())
                    WireFormat.WIRETYPE_FIXED32 -> ProtoNumber(input.readFloat())
                    else -> {
                        // Not a valid packed scalar field
                        return null
                    }
                }
                values.add(value)
            }
        } catch (_: Throwable) {
            return null
        }
        return values.ifEmpty { null }
    }

    /**
     * Encodes a list of scalar values as a packed repeated field.
     */
    fun encodePacked(values: List<ProtoValue>, tag: Int): ByteArray {
        var size = 0
        values.forEach { v ->
            size += v.computeSize(0) // compute without tag
        }
        val totalSize = CodedOutputStream.computeTagSize(tag) +
                computeRawVarint32Size(size) + size
        val dest = ByteArray(totalSize)
        val output = CodedOutputStream.newInstance(dest)
        output.writeTag(tag, WireFormat.WIRETYPE_LENGTH_DELIMITED)
        output.writeUInt32NoTag(size)
        values.forEach { v ->
            when (v) {
                is ProtoNumber -> {
                    when (v.value) {
                        is Float -> output.writeFloatNoTag(v.toFloat())
                        is Double -> output.writeDoubleNoTag(v.toDouble())
                        else -> output.writeInt64NoTag(v.toLong())
                    }
                }
                is ProtoBool -> output.writeBoolNoTag(v.value)
                else -> v.writeTo(output, 0)
            }
        }
        output.checkNoSpaceLeft()
        return dest
    }
}
