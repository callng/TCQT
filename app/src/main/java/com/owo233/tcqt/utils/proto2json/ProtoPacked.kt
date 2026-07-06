package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import kotlinx.serialization.json.JsonElement

enum class ProtoPackedType {
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
    BOOL,
    ENUM,
    RAW_VARINT,
    RAW_FIXED32,
    RAW_FIXED64;

    internal fun accepts(value: ProtoValue): Boolean = when (this) {
        BOOL -> value is ProtoBool
        else -> value is ProtoNumber && value.type == numberType
    }

    internal val numberType: ProtoNumberType
        get() = when (this) {
            INT32 -> ProtoNumberType.INT32
            INT64 -> ProtoNumberType.INT64
            UINT32 -> ProtoNumberType.UINT32
            UINT64 -> ProtoNumberType.UINT64
            SINT32 -> ProtoNumberType.SINT32
            SINT64 -> ProtoNumberType.SINT64
            FIXED32 -> ProtoNumberType.FIXED32
            FIXED64 -> ProtoNumberType.FIXED64
            SFIXED32 -> ProtoNumberType.SFIXED32
            SFIXED64 -> ProtoNumberType.SFIXED64
            FLOAT -> ProtoNumberType.FLOAT
            DOUBLE -> ProtoNumberType.DOUBLE
            ENUM -> ProtoNumberType.ENUM
            RAW_VARINT -> ProtoNumberType.RAW_VARINT
            RAW_FIXED32 -> ProtoNumberType.RAW_FIXED32
            RAW_FIXED64 -> ProtoNumberType.RAW_FIXED64
            BOOL -> error("BOOL does not have a ProtoNumberType")
        }

    companion object {
        fun infer(values: Iterable<ProtoValue>): ProtoPackedType {
            val iterator = values.iterator()
            if (!iterator.hasNext()) return RAW_VARINT
            val inferred = fromValue(iterator.next())
            while (iterator.hasNext()) {
                require(inferred.accepts(iterator.next())) {
                    "Packed repeated fields must contain one scalar type"
                }
            }
            return inferred
        }

        private fun fromValue(value: ProtoValue): ProtoPackedType = when (value) {
            is ProtoBool -> BOOL
            is ProtoNumber -> entries.first { it != BOOL && it.numberType == value.type }
            else -> throw IllegalArgumentException(
                "${value::class.simpleName} cannot be encoded as a packed scalar"
            )
        }
    }
}

/** A schema-known packed repeated scalar field. */
class ProtoPacked(
    val type: ProtoPackedType,
    value: ProtoList = ProtoList()
) : ProtoValue, Iterable<ProtoValue> {

    val value: ProtoList = ProtoList(value.value)

    override fun iterator(): Iterator<ProtoValue> = value.iterator()

    constructor(values: List<ProtoValue>) : this(
        ProtoPackedType.infer(values),
        ProtoList(values.toMutableList())
    )

    init {
        validateItems()
    }

    override fun toJson(): JsonElement = value.toJson()

    override fun computeSize(tag: Int): Int {
        ProtoUtils.requireValidTag(tag)
        val dataSize = computeDataSize()
        return CodedOutputStream.computeTagSize(tag) +
            CodedOutputStream.computeUInt32SizeNoTag(dataSize) + dataSize
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        ProtoUtils.requireValidTag(tag)
        val dataSize = computeDataSize()
        output.writeTag(tag, WireFormat.WIRETYPE_LENGTH_DELIMITED)
        output.writeUInt32NoTag(dataSize)
        value.forEachIndexed { index, item ->
            require(type.accepts(item)) {
                "Packed item $index is ${item::class.simpleName}, expected $type"
            }
            when (item) {
                is ProtoNumber -> item.writeNoTag(output)
                is ProtoBool -> item.writeNoTag(output)
                else -> error("Invalid packed item: ${item::class.simpleName}")
            }
        }
    }

    override fun size(): Int = value.size()
    fun isEmpty(): Boolean = value.isEmpty()
    operator fun get(index: Int): ProtoValue = value[index]

    override fun deepCopy(): ProtoPacked = ProtoPacked(type, value.deepCopy())

    override fun equals(other: Any?): Boolean =
        other is ProtoPacked && type == other.type && value == other.value

    override fun hashCode(): Int = 31 * type.hashCode() + value.hashCode()
    override fun toString(): String = value.toString()

    private fun computeDataSize(): Int {
        validateItems()
        return value.value.sumOf { item ->
            when (item) {
                is ProtoNumber -> item.computeSizeNoTag()
                is ProtoBool -> item.computeSizeNoTag()
                else -> error("Invalid packed item: ${item::class.simpleName}")
            }
        }
    }

    private fun validateItems() {
        value.forEachIndexed { index, item ->
            require(type.accepts(item)) {
                "Packed item $index is ${item::class.simpleName}, expected $type"
            }
        }
    }

    companion object {
        fun decode(data: ByteString, type: ProtoPackedType): ProtoPacked {
            val input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer())
            val values = arrayListOf<ProtoValue>()
            while (!input.isAtEnd) {
                values += when (type) {
                    ProtoPackedType.INT32 -> ProtoNumber(input.readInt32(), type = ProtoNumberType.INT32)
                    ProtoPackedType.INT64 -> ProtoNumber(input.readInt64(), type = ProtoNumberType.INT64)
                    ProtoPackedType.UINT32 -> ProtoNumber(input.readUInt32(), type = ProtoNumberType.UINT32)
                    ProtoPackedType.UINT64 -> ProtoNumber(input.readUInt64(), type = ProtoNumberType.UINT64)
                    ProtoPackedType.SINT32 -> ProtoNumber(input.readSInt32(), type = ProtoNumberType.SINT32)
                    ProtoPackedType.SINT64 -> ProtoNumber(input.readSInt64(), type = ProtoNumberType.SINT64)
                    ProtoPackedType.FIXED32 -> ProtoNumber(input.readFixed32(), type = ProtoNumberType.FIXED32)
                    ProtoPackedType.FIXED64 -> ProtoNumber(input.readFixed64(), type = ProtoNumberType.FIXED64)
                    ProtoPackedType.SFIXED32 -> ProtoNumber(input.readSFixed32(), type = ProtoNumberType.SFIXED32)
                    ProtoPackedType.SFIXED64 -> ProtoNumber(input.readSFixed64(), type = ProtoNumberType.SFIXED64)
                    ProtoPackedType.FLOAT -> ProtoNumber(input.readFloat(), type = ProtoNumberType.FLOAT)
                    ProtoPackedType.DOUBLE -> ProtoNumber(input.readDouble(), type = ProtoNumberType.DOUBLE)
                    ProtoPackedType.BOOL -> ProtoBool(input.readBool())
                    ProtoPackedType.ENUM -> ProtoNumber(input.readEnum(), type = ProtoNumberType.ENUM)
                    ProtoPackedType.RAW_VARINT -> ProtoNumber(
                        input.readUInt64(),
                        type = ProtoNumberType.RAW_VARINT
                    )
                    ProtoPackedType.RAW_FIXED32 -> ProtoNumber(
                        input.readFixed32(),
                        type = ProtoNumberType.RAW_FIXED32
                    )
                    ProtoPackedType.RAW_FIXED64 -> ProtoNumber(
                        input.readFixed64(),
                        type = ProtoNumberType.RAW_FIXED64
                    )
                }
            }
            return ProtoPacked(type, ProtoList(values))
        }
    }
}
