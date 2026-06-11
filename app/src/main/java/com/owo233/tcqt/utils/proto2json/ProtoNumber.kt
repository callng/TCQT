package com.owo233.tcqt.utils.proto2json

import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.JsonElement

class ProtoNumber(
    val value: Number,
    val isUnsigned: Boolean = false
) : ProtoValue {

    override fun toJson(): JsonElement {
        return when {
            isUnsigned && value is Int -> {
                val longVal = value.toLong() and 0xFFFFFFFFL
                longVal.json
            }
            isUnsigned && value is Long -> {
                if (value >= 0) value.json
                else (value.toULong()).toString().json
            }
            else -> value.json
        }
    }

    override fun computeSize(tag: Int): Int {
        return when {
            isUnsigned && value is Int -> CodedOutputStream.computeUInt32Size(tag, value)
            isUnsigned && value is Long -> CodedOutputStream.computeUInt64Size(tag, value)
            else -> when (value) {
                is Int -> CodedOutputStream.computeInt32Size(tag, value)
                is Long -> CodedOutputStream.computeInt64Size(tag, value)
                is Float -> CodedOutputStream.computeFloatSize(tag, value)
                is Double -> CodedOutputStream.computeDoubleSize(tag, value)
                is Byte -> CodedOutputStream.computeInt32Size(tag, value.toInt())
                is Short -> CodedOutputStream.computeInt32Size(tag, value.toInt())
                else -> error("ProtoNumber not support type: ${value::class.simpleName}")
            }
        }
    }

    override fun writeTo(output: CodedOutputStream, tag: Int) {
        when {
            isUnsigned && value is Int -> output.writeUInt32(tag, value)
            isUnsigned && value is Long -> output.writeUInt64(tag, value)
            else -> when (value) {
                is Int -> output.writeInt32(tag, value)
                is Long -> output.writeInt64(tag, value)
                is Float -> output.writeFloat(tag, value)
                is Double -> output.writeDouble(tag, value)
                is Byte -> output.writeInt32(tag, value.toInt())
                is Short -> output.writeInt32(tag, value.toInt())
                else -> error("ProtoNumber not support type: ${value::class.simpleName}")
            }
        }
    }

    fun toInt(): Int = value.toInt()

    fun toLong(): Long = value.toLong()

    fun toUInt(): UInt = value.toInt().toUInt()

    fun toULong(): ULong = value.toLong().toULong()

    fun toFloat(): Float = value.toFloat()

    fun toDouble(): Double = value.toDouble()

    fun toBoolean(): Boolean = value.toInt() != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoNumber) return false
        return value.toDouble() == other.value.toDouble() && isUnsigned == other.isUnsigned
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + isUnsigned.hashCode()
        return result
    }

    override fun toString(): String = "$value"
}
