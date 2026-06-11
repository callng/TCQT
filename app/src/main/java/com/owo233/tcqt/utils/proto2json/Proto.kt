package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.ByteString
import com.owo233.tcqt.utils.proto2json.ProtoUtils.walkPairTags

fun <K, V> protobufOf(vararg pairs: Pair<K, V>): ProtoMap {
    val map = ProtoMap()
    pairs.forEach {
        val (k, v) = it
        when (k) {
            is Number -> map[k.toInt()] = ProtoUtils.any2proto(v!!)
            is Pair<*, *> -> {
                val tags = walkPairTags(k)
                map.set(*tags.toIntArray(), v = ProtoUtils.any2proto(v!!))
            }

            else -> error("Not support type for tag: ${k.toString()}")
        }
    }
    return map
}

fun protobufMapOf(struct: ProtoMap.() -> Unit): ProtoMap {
    return ProtoMap().apply(struct)
}

fun protobufListOf(vararg items: ProtoValue): ProtoList {
    return ProtoList(arrayListOf(*items))
}

fun protoBoolOf(value: Boolean): ProtoBool = ProtoBool(value)

fun protoIntOf(value: Int): ProtoNumber = ProtoNumber(value)

fun protoLongOf(value: Long): ProtoNumber = ProtoNumber(value)

fun protoFloatOf(value: Float): ProtoNumber = ProtoNumber(value)

fun protoDoubleOf(value: Double): ProtoNumber = ProtoNumber(value)

fun protoUInt32Of(value: UInt): ProtoNumber = ProtoNumber(value.toInt(), isUnsigned = true)

fun protoUInt64Of(value: ULong): ProtoNumber = ProtoNumber(value.toLong(), isUnsigned = true)

fun protoBytesOf(value: ByteArray): ProtoByteString = ProtoByteString(ByteString.copyFrom(value))

fun protoStringOf(value: String): ProtoByteString = ProtoByteString(ByteString.copyFromUtf8(value))

val Number.proto: ProtoNumber
    get() = ProtoNumber(this)

val Boolean.proto: ProtoBool
    get() = ProtoBool(this)

val ByteString.proto: ProtoByteString
    get() = ProtoByteString(this)

val ByteArray.proto: ProtoByteString
    get() = ProtoByteString(ByteString.copyFrom(this))

val String.proto: ProtoByteString
    get() = ProtoByteString(ByteString.copyFromUtf8(this))

val ProtoValue.asString: ByteString
    get() = (this as ProtoByteString).value

val ProtoValue.asNumber: Number
    get() = (this as ProtoNumber).value

val ProtoValue.asInt: Int
    get() = when (this) {
        is ProtoNumber -> value.toInt()
        is ProtoBool -> if (value) 1 else 0
        else -> error("Cannot convert ${this::class.simpleName} to Int")
    }

val ProtoValue.asLong: Long
    get() = when (this) {
        is ProtoNumber -> value.toLong()
        is ProtoBool -> if (value) 1L else 0L
        else -> error("Cannot convert ${this::class.simpleName} to Long")
    }

val ProtoValue.asFloat: Float
    get() = (this as ProtoNumber).value.toFloat()

val ProtoValue.asDouble: Double
    get() = (this as ProtoNumber).value.toDouble()

val ProtoValue.asBoolean: Boolean
    get() = when (this) {
        is ProtoBool -> value
        is ProtoNumber -> value.toInt() != 0
        else -> error("Cannot convert ${this::class.simpleName} to Boolean")
    }

val ProtoValue.asUInt: UInt
    get() = when (this) {
        is ProtoNumber if isUnsigned -> value.toInt().toUInt()
        is ProtoNumber -> value.toInt().toUInt()
        else -> error("Cannot convert ${this::class.simpleName} to UInt")
    }

val ProtoValue.asULong: ULong
    get() = when (this) {
        is ProtoNumber if isUnsigned -> value.toLong().toULong()
        is ProtoNumber -> value.toLong().toULong()
        else -> error("Cannot convert ${this::class.simpleName} to ULong")
    }

val ProtoValue.asMap: ProtoMap
    get() = (this as ProtoMap)

val ProtoValue.asList: ProtoList
    get() = (this as ProtoList)

val ProtoValue.asByteArray: ByteArray
    get() = when (this) {
        is ProtoMap -> toByteArray()
        is ProtoByteString -> toByteArray()
        else -> error("Cannot convert ${this::class.simpleName} to ByteArray")
    }

val ProtoValue.asUtf8String: String
    get() = (this as ProtoByteString).toUtfString()

val ProtoValue.asHexString: String
    get() = when (this) {
        is ProtoByteString -> toByteArray().joinToString("") { "%02x".format(it) }
        is ProtoNumber -> "0x${toLong().toULong().toString(16)}"
        else -> error("Cannot convert ${this::class.simpleName} to hex string")
    }

fun ProtoValue?.toNullableInt(default: Int = 0): Int {
    return this?.asInt ?: default
}

fun ProtoValue?.toNullableLong(default: Long = 0L): Long {
    return this?.asLong ?: default
}

fun ProtoValue?.toNullableBoolean(default: Boolean = false): Boolean {
    return this?.asBoolean ?: default
}

fun ProtoValue?.toNullableUtf8String(default: String = ""): String {
    return try {
        this?.asUtf8String ?: default
    } catch (_: Throwable) {
        default
    }
}
