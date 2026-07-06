package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet

/**
 * Controls how schema-free length-delimited fields are represented.
 *
 * [COMPATIBLE] restores the convenient behavior used by the original library:
 * printable UTF-8 data remains [ProtoByteString], while non-text payloads that
 * are valid protobuf messages are recursively decoded as [ProtoMap]. This lets
 * existing code keep using paths such as `message[6, 2]` and runtime checks such
 * as `value is ProtoMap`.
 *
 * [WIRE_PRESERVING] never guesses the logical meaning of a length-delimited
 * field. Every such field remains [ProtoByteString], because strings, bytes,
 * embedded messages and packed repeated fields share the same wire type.
 */
enum class ProtoDecodeMode {
    COMPATIBLE,
    WIRE_PRESERVING
}

object ProtoUtils {

    /**
     * Decodes a protobuf stream without a descriptor.
     *
     * The default [ProtoDecodeMode.COMPATIBLE] mode preserves the original
     * proto2json API experience by recursively decoding likely embedded
     * messages. Use [ProtoDecodeMode.WIRE_PRESERVING] for wire inspection or
     * whenever automatic message detection is undesirable.
     *
     * Scalar wire types are always preserved with RAW_* number types.
     */
    @JvmOverloads
    fun decodeFromByteArray(
        data: ByteArray,
        mode: ProtoDecodeMode = ProtoDecodeMode.COMPATIBLE
    ): ProtoMap = decodeUnknownFieldSet(
        UnknownFieldSet.parseFrom(data),
        mode,
        depth = 0
    )

    @JvmOverloads
    fun decodeFromByteString(
        data: ByteString,
        mode: ProtoDecodeMode = ProtoDecodeMode.COMPATIBLE
    ): ProtoMap = decodeUnknownFieldSet(
        UnknownFieldSet.parseFrom(data),
        mode,
        depth = 0
    )

    @JvmOverloads
    fun decodeEmbeddedMessage(
        data: ProtoByteString,
        mode: ProtoDecodeMode = ProtoDecodeMode.COMPATIBLE
    ): ProtoMap = decodeFromByteString(data.value, mode)

    fun decodePacked(data: ProtoByteString, type: ProtoPackedType): ProtoPacked =
        ProtoPacked.decode(data.value, type)

    fun encodeToByteArray(protoMap: ProtoMap): ByteArray {
        val size = protoMap.computeSizeDirectly()
        val destination = ByteArray(size)
        val output = CodedOutputStream.newInstance(destination)
        protoMap.writeFieldsTo(output)
        output.checkNoSpaceLeft()
        return destination
    }

    fun encodePacked(values: List<ProtoValue>, tag: Int): ByteArray =
        encodeSingleField(ProtoPacked(values), tag)

    fun encodePacked(values: List<ProtoValue>, tag: Int, type: ProtoPackedType): ByteArray =
        encodeSingleField(ProtoPacked(type, ProtoList(values.toMutableList())), tag)

    fun encodeSingleField(value: ProtoValue, tag: Int): ByteArray {
        requireValidTag(tag)
        val destination = ByteArray(value.computeSize(tag))
        val output = CodedOutputStream.newInstance(destination)
        value.writeTo(output, tag)
        output.checkNoSpaceLeft()
        return destination
    }

    internal fun any2proto(any: Any?): ProtoValue = when (any) {
        null -> throw IllegalArgumentException(
            "Protocol Buffers cannot represent null without a schema wrapper"
        )
        is ProtoValue -> any
        is Boolean -> any.proto
        is Number -> any.proto
        is UByte -> protoUInt32Of(any.toUInt())
        is UShort -> protoUInt32Of(any.toUInt())
        is UInt -> protoUInt32Of(any)
        is ULong -> protoUInt64Of(any)
        is ByteArray -> any.proto
        is String -> any.proto
        is ByteString -> any.proto
        is Array<*> -> ProtoList(any.mapTo(arrayListOf()) { any2proto(it) })
        is BooleanArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is ShortArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is IntArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is LongArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is FloatArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is DoubleArray -> ProtoList(any.mapTo(arrayListOf()) { it.proto })
        is Collection<*> -> ProtoList(any.mapTo(arrayListOf()) { any2proto(it) })
        is Map<*, *> -> ProtoMap(linkedMapOf<Int, ProtoValue>().apply {
            any.forEach { (key, value) ->
                val tag = key.toFieldNumber()
                put(tag, any2proto(value))
            }
        })
        is Pair<*, *> -> {
            val path = when (val key = any.first) {
                is Number, is UByte, is UShort, is UInt, is ULong ->
                    intArrayOf(key.toFieldNumber())
                is Pair<*, *> -> walkPairTags(key).toIntArray()
                else -> throw IllegalArgumentException("Unsupported protobuf tag path: $key")
            }
            ProtoMap().apply { set(*path, v = any2proto(any.second)) }
        }
        else -> throw IllegalArgumentException(
            "Unsupported protobuf value: ${any::class.qualifiedName}"
        )
    }

    fun walkPairTags(pair: Pair<*, *>): List<Int> = buildList {
        fun appendPath(node: Any?) {
            when (node) {
                is Pair<*, *> -> {
                    appendPath(node.first)
                    appendPath(node.second)
                }
                is Number, is UByte, is UShort, is UInt, is ULong ->
                    add(node.toFieldNumber())
                else -> throw IllegalArgumentException(
                    "Tag path contains a non-numeric value: $node"
                )
            }
        }
        appendPath(pair)
    }

    internal fun Any?.toFieldNumber(): Int {
        val value = when (this) {
            is Byte -> toLong()
            is Short -> toLong()
            is Int -> toLong()
            is Long -> this
            is Float -> {
                require(isFinite() && this % 1f == 0f) {
                    "Field number must be an integer: $this"
                }
                toLong()
            }
            is Double -> {
                require(isFinite() && this % 1.0 == 0.0) {
                    "Field number must be an integer: $this"
                }
                toLong()
            }
            is UByte -> toLong()
            is UShort -> toLong()
            is UInt -> toLong()
            is ULong -> {
                require(this <= Long.MAX_VALUE.toULong()) {
                    "Field number is too large: $this"
                }
                toLong()
            }
            is Number -> {
                val decimal = toDouble()
                require(decimal.isFinite() && decimal % 1.0 == 0.0) {
                    "Field number must be an integer: $this"
                }
                require(decimal in 1.0..MAX_FIELD_NUMBER.toDouble()) {
                    "Invalid protobuf field number $this; expected 1..$MAX_FIELD_NUMBER"
                }
                decimal.toLong()
            }
            else -> throw IllegalArgumentException(
                "Protobuf field tag must be numeric: $this"
            )
        }
        require(value in 1L..MAX_FIELD_NUMBER.toLong()) {
            "Invalid protobuf field number $value; expected 1..$MAX_FIELD_NUMBER"
        }
        return value.toInt()
    }

    internal fun requireValidTag(tag: Int) {
        require(tag in 1..MAX_FIELD_NUMBER) {
            "Invalid protobuf field number $tag; expected 1..$MAX_FIELD_NUMBER"
        }
    }

    private fun decodeUnknownFieldSet(
        set: UnknownFieldSet,
        mode: ProtoDecodeMode,
        depth: Int
    ): ProtoMap {
        val destination = ProtoMap()
        set.asMap().forEach { (tag, field) ->
            field.varintList.forEach { value ->
                destination.append(
                    tag,
                    ProtoNumber(value, type = ProtoNumberType.RAW_VARINT)
                )
            }
            field.fixed32List.forEach { value ->
                destination.append(
                    tag,
                    ProtoNumber(value, type = ProtoNumberType.RAW_FIXED32)
                )
            }
            field.fixed64List.forEach { value ->
                destination.append(
                    tag,
                    ProtoNumber(value, type = ProtoNumberType.RAW_FIXED64)
                )
            }
            field.lengthDelimitedList.forEach { value ->
                destination.append(
                    tag,
                    decodeLengthDelimited(value, mode, depth)
                )
            }
            field.groupList.forEach { group ->
                destination.append(
                    tag,
                    ProtoGroup(decodeUnknownFieldSet(group, mode, depth + 1))
                )
            }
        }
        return destination
    }

    private fun decodeLengthDelimited(
        value: ByteString,
        mode: ProtoDecodeMode,
        depth: Int
    ): ProtoValue {
        if (mode == ProtoDecodeMode.WIRE_PRESERVING) {
            return ProtoByteString(value)
        }

        // Keep text as bytes so existing asUtf8String calls and string/bytes
        // semantics remain stable. Embedded message payloads normally contain
        // non-printable tag bytes, so they proceed to recursive parsing below.
        if (value.isPrintableUtf8()) {
            return ProtoByteString(value)
        }

        if (depth >= MAX_COMPATIBLE_NESTING_DEPTH) {
            return ProtoByteString(value)
        }

        val nestedSet = runCatching {
            UnknownFieldSet.parseFrom(value)
        }.getOrNull() ?: return ProtoByteString(value)

        // Empty input and some non-message payloads can parse as an empty set.
        // Treating those as a message would break ordinary bytes fields.
        if (nestedSet.asMap().isEmpty()) {
            return ProtoByteString(value)
        }

        return decodeUnknownFieldSet(nestedSet, mode, depth + 1)
    }

    private fun ByteString.isPrintableUtf8(): Boolean {
        if (isEmpty) return true
        if (!isValidUtf8) return false

        return toStringUtf8().all { char ->
            !char.isISOControl() || char == '\t' || char == '\n' || char == '\r'
        }
    }

    private const val MAX_FIELD_NUMBER = (1 shl 29) - 1
    private const val MAX_COMPATIBLE_NESTING_DEPTH = 100
}

internal fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    val alphabet = "0123456789abcdef"
    for (index in indices) {
        val byte = this[index].toInt() and 0xFF
        chars[index * 2] = alphabet[byte ushr 4]
        chars[index * 2 + 1] = alphabet[byte and 0x0F]
    }
    return String(chars)
}
