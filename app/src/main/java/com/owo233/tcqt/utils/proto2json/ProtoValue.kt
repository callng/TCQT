package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

sealed interface ProtoValue {

    fun toJson(): JsonElement

    fun computeSize(tag: Int): Int

    fun writeTo(output: CodedOutputStream, tag: Int)

    fun computeSizeDirectly(): Int =
        throw UnsupportedOperationException("${this::class.simpleName} is not a message container")

    fun has(vararg tags: Int): Boolean = false

    operator fun contains(tag: Int): Boolean = false

    operator fun set(tag: Int, v: ProtoValue) {
        throw UnsupportedOperationException("${this::class.simpleName} does not support field assignment")
    }

    operator fun set(tag: Int, v: Number) {
        set(tag, v.proto)
    }

    operator fun get(vararg tags: Int): ProtoValue =
        throw UnsupportedOperationException("${this::class.simpleName} is not a message container")

    fun remove(tag: Int): Boolean = false

    fun add(v: ProtoValue) {
        throw UnsupportedOperationException("${this::class.simpleName} is not a repeated field")
    }

    fun size(): Int =
        throw UnsupportedOperationException("${this::class.simpleName} has no collection size")

    fun deepCopy(): ProtoValue = this

    val isMap: Boolean get() = this is ProtoMap
    val isList: Boolean get() = this is ProtoList
    val isNumber: Boolean get() = this is ProtoNumber
    val isByteString: Boolean get() = this is ProtoByteString
    val isString: Boolean get() = this is ProtoString
    val isBool: Boolean get() = this is ProtoBool
    val isGroup: Boolean get() = this is ProtoGroup
    val isPacked: Boolean get() = this is ProtoPacked
}
