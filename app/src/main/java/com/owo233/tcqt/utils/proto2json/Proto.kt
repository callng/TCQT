package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString

fun <K, V> protobufOf(vararg pairs: Pair<K, V>): ProtoMap = ProtoMap().apply {
    pairs.forEach { (key, value) ->
        when (key) {
            is Number, is UByte, is UShort, is UInt, is ULong ->
                set(ProtoUtils.run { key.toFieldNumber() }, ProtoUtils.any2proto(value))
            is Pair<*, *> -> set(
                *ProtoUtils.walkPairTags(key).toIntArray(),
                v = ProtoUtils.any2proto(value)
            )
            else -> throw IllegalArgumentException("Unsupported protobuf tag path: $key")
        }
    }
}

fun protobufMapOf(struct: ProtoMap.() -> Unit): ProtoMap = ProtoMap().apply(struct)

fun protobufListOf(vararg items: ProtoValue): ProtoList =
    ProtoList(items.toMutableList())

fun protoBoolOf(value: Boolean): ProtoBool = ProtoBool(value)
fun protoIntOf(value: Int): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.INT32)
fun protoLongOf(value: Long): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.INT64)
fun protoUInt32Of(value: UInt): ProtoNumber =
    ProtoNumber(value.toInt(), type = ProtoNumberType.UINT32)
fun protoUInt64Of(value: ULong): ProtoNumber =
    ProtoNumber(value.toLong(), type = ProtoNumberType.UINT64)
fun protoSInt32Of(value: Int): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.SINT32)
fun protoSInt64Of(value: Long): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.SINT64)
fun protoFixed32Of(value: UInt): ProtoNumber =
    ProtoNumber(value.toInt(), type = ProtoNumberType.FIXED32)
fun protoFixed64Of(value: ULong): ProtoNumber =
    ProtoNumber(value.toLong(), type = ProtoNumberType.FIXED64)
fun protoSFixed32Of(value: Int): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.SFIXED32)
fun protoSFixed64Of(value: Long): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.SFIXED64)
fun protoFloatOf(value: Float): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.FLOAT)
fun protoDoubleOf(value: Double): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.DOUBLE)
fun protoEnumOf(value: Int): ProtoNumber = ProtoNumber(value, type = ProtoNumberType.ENUM)
fun protoBytesOf(value: ByteArray): ProtoByteString = ProtoByteString(ByteString.copyFrom(value))
fun protoStringOf(value: String): ProtoString = ProtoString(value)
fun protoGroupOf(struct: ProtoMap.() -> Unit): ProtoGroup = ProtoGroup(ProtoMap().apply(struct))
fun protoPackedOf(type: ProtoPackedType, vararg values: ProtoValue): ProtoPacked =
    ProtoPacked(type, ProtoList(values.toMutableList()))

val Number.proto: ProtoNumber
    get() = ProtoNumber(this)

val Boolean.proto: ProtoBool
    get() = ProtoBool(this)

val ByteString.proto: ProtoByteString
    get() = ProtoByteString(this)

val ByteArray.proto: ProtoByteString
    get() = ProtoByteString(ByteString.copyFrom(this))

val String.proto: ProtoString
    get() = ProtoString(this)

val ProtoValue.asString: ByteString
    get() = when (this) {
        is ProtoByteString -> value
        is ProtoString -> toByteString()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to ByteString")
    }

val ProtoValue.asNumber: Number
    get() = (this as? ProtoNumber)?.value
        ?: throw IllegalStateException("Cannot convert ${this::class.simpleName} to Number")

val ProtoValue.asInt: Int
    get() = when (this) {
        is ProtoNumber -> toInt()
        is ProtoBool -> toInt()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to Int")
    }

val ProtoValue.asLong: Long
    get() = when (this) {
        is ProtoNumber -> toLong()
        is ProtoBool -> toLong()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to Long")
    }

val ProtoValue.asFloat: Float
    get() = (this as? ProtoNumber)?.toFloat()
        ?: throw IllegalStateException("Cannot convert ${this::class.simpleName} to Float")

val ProtoValue.asDouble: Double
    get() = (this as? ProtoNumber)?.toDouble()
        ?: throw IllegalStateException("Cannot convert ${this::class.simpleName} to Double")

val ProtoValue.asBoolean: Boolean
    get() = when (this) {
        is ProtoBool -> value
        is ProtoNumber -> toBoolean()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to Boolean")
    }

val ProtoValue.asUInt: UInt
    get() = (this as? ProtoNumber)?.toUInt()
        ?: throw IllegalStateException("Cannot convert ${this::class.simpleName} to UInt")

val ProtoValue.asULong: ULong
    get() = (this as? ProtoNumber)?.toULong()
        ?: throw IllegalStateException("Cannot convert ${this::class.simpleName} to ULong")

val ProtoValue.asMap: ProtoMap
    get() = this as? ProtoMap
        ?: throw IllegalStateException("${this::class.simpleName} is not ProtoMap")

val ProtoValue.asList: ProtoList
    get() = this as? ProtoList
        ?: throw IllegalStateException("${this::class.simpleName} is not ProtoList")

val ProtoValue.asGroup: ProtoGroup
    get() = this as? ProtoGroup
        ?: throw IllegalStateException("${this::class.simpleName} is not ProtoGroup")

val ProtoValue.asPacked: ProtoPacked
    get() = this as? ProtoPacked
        ?: throw IllegalStateException("${this::class.simpleName} is not ProtoPacked")

val ProtoValue.asByteArray: ByteArray
    get() = when (this) {
        is ProtoMap -> toByteArray()
        is ProtoByteString -> toByteArray()
        is ProtoString -> toByteArray()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to ByteArray")
    }

val ProtoValue.asUtf8String: String
    get() = when (this) {
        is ProtoString -> value
        is ProtoByteString -> toUtfString()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to UTF-8 String")
    }

val ProtoValue.asHexString: String
    get() = when (this) {
        is ProtoByteString -> toHexString()
        is ProtoString -> toByteArray().toHexString()
        is ProtoNumber -> "0x${toLong().toULong().toString(16)}"
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to hex String")
    }

fun ProtoValue?.toNullableInt(default: Int = 0): Int = when (this) {
    null -> default
    is ProtoNumber -> toInt()
    is ProtoBool -> toInt()
    else -> default
}

fun ProtoValue?.toNullableLong(default: Long = 0L): Long = when (this) {
    null -> default
    is ProtoNumber -> toLong()
    is ProtoBool -> toLong()
    else -> default
}

fun ProtoValue?.toNullableBoolean(default: Boolean = false): Boolean = when (this) {
    null -> default
    is ProtoBool -> value
    is ProtoNumber -> toBoolean()
    else -> default
}

fun ProtoValue?.toNullableUtf8String(default: String = ""): String = when (this) {
    is ProtoString -> value
    is ProtoByteString -> if (isValidUtf8()) toUtfString() else default
    else -> default
}
