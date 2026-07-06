package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

/**
 * Logical scalar encoding used by a numeric protobuf field.
 *
 * RAW_* variants are used when decoding without a descriptor. A protobuf wire
 * stream does not contain enough information to distinguish, for example,
 * int64 from uint64 or double from fixed64.
 */
enum class ProtoNumberType {
    INT32,
    INT64,
    UINT32,
    UINT64,
    SINT32,
    SINT64,
    FIXED32,
    FIXED64,
    SFIXED32,
    SFIXED64,
    FLOAT,
    DOUBLE,
    ENUM,
    RAW_VARINT,
    RAW_FIXED32,
    RAW_FIXED64;

    val isUnsigned: Boolean
        get() = this == UINT32 || this == UINT64 || this == FIXED32 ||
            this == FIXED64 || this == RAW_VARINT || this == RAW_FIXED32 ||
            this == RAW_FIXED64

    companion object {
        internal fun infer(value: Number, isUnsigned: Boolean): ProtoNumberType = when (value) {
            is Byte, is Short, is Int -> if (isUnsigned) UINT32 else INT32
            is Long -> if (isUnsigned) UINT64 else INT64
            is Float -> {
                require(!isUnsigned) { "Float cannot be encoded as an unsigned integer" }
                FLOAT
            }
            is Double -> {
                require(!isUnsigned) { "Double cannot be encoded as an unsigned integer" }
                DOUBLE
            }
            else -> throw IllegalArgumentException(
                "Unsupported numeric type: ${value::class.qualifiedName}"
            )
        }
    }
}

class ProtoNumber(
    rawValue: Number,
    isUnsigned: Boolean = false,
    val type: ProtoNumberType = ProtoNumberType.infer(rawValue, isUnsigned)
) : ProtoValue {

    val value: Number = normalize(rawValue, type)

    val isUnsigned: Boolean get() = type.isUnsigned

    override fun toJson(): JsonElement = when (type) {
        ProtoNumberType.INT64,
        ProtoNumberType.SINT64,
        ProtoNumberType.SFIXED64 -> value.toLong().toString().json

        ProtoNumberType.UINT64,
        ProtoNumberType.FIXED64,
        ProtoNumberType.RAW_VARINT,
        ProtoNumberType.RAW_FIXED64 -> value.toLong().toULong().toString().json

        ProtoNumberType.UINT32,
        ProtoNumberType.FIXED32,
        ProtoNumberType.RAW_FIXED32 -> (value.toInt().toLong() and UINT32_MASK).json

        ProtoNumberType.FLOAT -> value.toFloat().toProtoJson()
        ProtoNumberType.DOUBLE -> value.toDouble().toProtoJson()

        ProtoNumberType.INT32,
        ProtoNumberType.SINT32,
        ProtoNumberType.SFIXED32,
        ProtoNumberType.ENUM -> value.toInt().json
    }

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        return CodedOutputStream.computeTagSize(tag) + computeSizeNoTag()
    }

    internal fun computeSizeNoTag(): Int = when (type) {
        ProtoNumberType.INT32 -> CodedOutputStream.computeInt32SizeNoTag(value.toInt())
        ProtoNumberType.INT64 -> CodedOutputStream.computeInt64SizeNoTag(value.toLong())
        ProtoNumberType.UINT32 -> CodedOutputStream.computeUInt32SizeNoTag(value.toInt())
        ProtoNumberType.UINT64 -> CodedOutputStream.computeUInt64SizeNoTag(value.toLong())
        ProtoNumberType.SINT32 -> CodedOutputStream.computeSInt32SizeNoTag(value.toInt())
        ProtoNumberType.SINT64 -> CodedOutputStream.computeSInt64SizeNoTag(value.toLong())
        ProtoNumberType.FIXED32 -> CodedOutputStream.computeFixed32SizeNoTag(value.toInt())
        ProtoNumberType.FIXED64 -> CodedOutputStream.computeFixed64SizeNoTag(value.toLong())
        ProtoNumberType.SFIXED32 -> CodedOutputStream.computeSFixed32SizeNoTag(value.toInt())
        ProtoNumberType.SFIXED64 -> CodedOutputStream.computeSFixed64SizeNoTag(value.toLong())
        ProtoNumberType.FLOAT -> CodedOutputStream.computeFloatSizeNoTag(value.toFloat())
        ProtoNumberType.DOUBLE -> CodedOutputStream.computeDoubleSizeNoTag(value.toDouble())
        ProtoNumberType.ENUM -> CodedOutputStream.computeEnumSizeNoTag(value.toInt())
        ProtoNumberType.RAW_VARINT -> CodedOutputStream.computeUInt64SizeNoTag(value.toLong())
        ProtoNumberType.RAW_FIXED32 -> CodedOutputStream.computeFixed32SizeNoTag(value.toInt())
        ProtoNumberType.RAW_FIXED64 -> CodedOutputStream.computeFixed64SizeNoTag(value.toLong())
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        when (type) {
            ProtoNumberType.INT32 -> output.writeInt32(tag, value.toInt())
            ProtoNumberType.INT64 -> output.writeInt64(tag, value.toLong())
            ProtoNumberType.UINT32 -> output.writeUInt32(tag, value.toInt())
            ProtoNumberType.UINT64 -> output.writeUInt64(tag, value.toLong())
            ProtoNumberType.SINT32 -> output.writeSInt32(tag, value.toInt())
            ProtoNumberType.SINT64 -> output.writeSInt64(tag, value.toLong())
            ProtoNumberType.FIXED32 -> output.writeFixed32(tag, value.toInt())
            ProtoNumberType.FIXED64 -> output.writeFixed64(tag, value.toLong())
            ProtoNumberType.SFIXED32 -> output.writeSFixed32(tag, value.toInt())
            ProtoNumberType.SFIXED64 -> output.writeSFixed64(tag, value.toLong())
            ProtoNumberType.FLOAT -> output.writeFloat(tag, value.toFloat())
            ProtoNumberType.DOUBLE -> output.writeDouble(tag, value.toDouble())
            ProtoNumberType.ENUM -> output.writeEnum(tag, value.toInt())
            ProtoNumberType.RAW_VARINT -> output.writeUInt64(tag, value.toLong())
            ProtoNumberType.RAW_FIXED32 -> output.writeFixed32(tag, value.toInt())
            ProtoNumberType.RAW_FIXED64 -> output.writeFixed64(tag, value.toLong())
        }
    }

    internal fun writeNoTag(output: CodedOutputStream) {
        when (type) {
            ProtoNumberType.INT32 -> output.writeInt32NoTag(value.toInt())
            ProtoNumberType.INT64 -> output.writeInt64NoTag(value.toLong())
            ProtoNumberType.UINT32 -> output.writeUInt32NoTag(value.toInt())
            ProtoNumberType.UINT64 -> output.writeUInt64NoTag(value.toLong())
            ProtoNumberType.SINT32 -> output.writeSInt32NoTag(value.toInt())
            ProtoNumberType.SINT64 -> output.writeSInt64NoTag(value.toLong())
            ProtoNumberType.FIXED32 -> output.writeFixed32NoTag(value.toInt())
            ProtoNumberType.FIXED64 -> output.writeFixed64NoTag(value.toLong())
            ProtoNumberType.SFIXED32 -> output.writeSFixed32NoTag(value.toInt())
            ProtoNumberType.SFIXED64 -> output.writeSFixed64NoTag(value.toLong())
            ProtoNumberType.FLOAT -> output.writeFloatNoTag(value.toFloat())
            ProtoNumberType.DOUBLE -> output.writeDoubleNoTag(value.toDouble())
            ProtoNumberType.ENUM -> output.writeEnumNoTag(value.toInt())
            ProtoNumberType.RAW_VARINT -> output.writeUInt64NoTag(value.toLong())
            ProtoNumberType.RAW_FIXED32 -> output.writeFixed32NoTag(value.toInt())
            ProtoNumberType.RAW_FIXED64 -> output.writeFixed64NoTag(value.toLong())
        }
    }

    fun toInt(): Int = value.toInt()
    fun toLong(): Long = value.toLong()
    fun toUInt(): UInt = value.toInt().toUInt()
    fun toULong(): ULong = value.toLong().toULong()
    fun toFloat(): Float = value.toFloat()
    fun toDouble(): Double = value.toDouble()
    fun toBoolean(): Boolean = when (value) {
        is Float -> value != 0f
        is Double -> value != 0.0
        else -> value.toLong() != 0L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is ProtoNumber && type == other.type && value == other.value
    }

    override fun hashCode(): Int = 31 * type.hashCode() + value.hashCode()

    override fun toString(): String = when {
        type == ProtoNumberType.UINT32 || type == ProtoNumberType.FIXED32 ||
            type == ProtoNumberType.RAW_FIXED32 ->
            (value.toInt().toLong() and UINT32_MASK).toString()

        type.isUnsigned -> value.toLong().toULong().toString()
        else -> value.toString()
    }

    companion object {
        private const val UINT32_MASK = 0xFFFF_FFFFL

        private fun normalize(value: Number, type: ProtoNumberType): Number = when (type) {
            ProtoNumberType.FLOAT -> value.toFloat()
            ProtoNumberType.DOUBLE -> value.toDouble()
            ProtoNumberType.INT64,
            ProtoNumberType.UINT64,
            ProtoNumberType.SINT64,
            ProtoNumberType.FIXED64,
            ProtoNumberType.SFIXED64,
            ProtoNumberType.RAW_VARINT,
            ProtoNumberType.RAW_FIXED64 -> value.toLong()

            else -> value.toInt()
        }

        private fun Float.toProtoJson(): JsonElement = when {
            isNaN() -> "NaN".json
            this == Float.POSITIVE_INFINITY -> "Infinity".json
            this == Float.NEGATIVE_INFINITY -> "-Infinity".json
            else -> json
        }

        private fun Double.toProtoJson(): JsonElement = when {
            isNaN() -> "NaN".json
            this == Double.POSITIVE_INFINITY -> "Infinity".json
            this == Double.NEGATIVE_INFINITY -> "-Infinity".json
            else -> json
        }
    }
}
