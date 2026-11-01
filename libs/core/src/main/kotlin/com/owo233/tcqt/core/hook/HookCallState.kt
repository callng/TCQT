package com.owo233.tcqt.core.hook

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Executable

/** Shared outcome state for interceptor and generated-bridge adapters. */
open class HookCallState(
    override val method: Member,
    override val thisObject: Any?,
    override var args: Array<Any?>,
) : HookParam {
    private var value: Any? = null
    private var failure: Throwable? = null
    var isReturnEarly = false
        private set

    override var result: Any?
        get() = value
        set(result) {
            value = HookContract.result(method, result)
            failure = null
            isReturnEarly = true
        }
    override var throwable: Throwable?
        get() = failure
        set(error) {
            failure = error
            value = if (error == null) HookContract.result(method, null) else null
            isReturnEarly = true
        }

    data class Outcome(val result: Any?, val throwable: Throwable?, val early: Boolean)
    fun snapshot() = Outcome(value, failure, isReturnEarly)
    fun restore(state: Outcome) {
        value = state.result
        failure = state.throwable
        isReturnEarly = state.early
    }
    fun finish(): Any? { failure?.let { throw it }; return value }
}

object HookContract {
    fun arguments(member: Member, values: Array<Any?>) {
        val types = (member as Executable).parameterTypes
        require(values.size == types.size) { "Wrong Hook argument count for $member" }
        types.forEachIndexed { index, type ->
            val value = values[index]
            if (type.isPrimitive) {
                require(value != null && resultType(type, value)) {
                    "Invalid primitive Hook argument $index for $member"
                }
            } else require(value == null || type.isInstance(value)) { "Invalid Hook argument $index for $member" }
        }
    }

    private fun resultType(type: Class<*>, value: Any): Boolean = when (type) {
        java.lang.Boolean.TYPE -> value is Boolean
        java.lang.Byte.TYPE -> value is Byte
        java.lang.Short.TYPE -> value is Short
        java.lang.Character.TYPE -> value is Char
        java.lang.Integer.TYPE -> value is Int
        java.lang.Long.TYPE -> value is Long
        java.lang.Float.TYPE -> value is Float
        java.lang.Double.TYPE -> value is Double
        else -> false
    }

    fun result(member: Member, value: Any?): Any? {
        val type = (member as? Method)?.returnType ?: Void.TYPE
        if (type == Void.TYPE) return null
        if (value == null || value === Unit) return if (!type.isPrimitive) null else when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> error("Unsupported return type")
        }
        val boxed = when (type) {
            java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
            java.lang.Byte.TYPE -> Byte::class.javaObjectType
            java.lang.Short.TYPE -> Short::class.javaObjectType
            java.lang.Character.TYPE -> Char::class.javaObjectType
            java.lang.Integer.TYPE -> Int::class.javaObjectType
            java.lang.Long.TYPE -> Long::class.javaObjectType
            java.lang.Float.TYPE -> Float::class.javaObjectType
            java.lang.Double.TYPE -> Double::class.javaObjectType
            else -> type
        }
        if (!boxed.isInstance(value)) throw ClassCastException("Invalid Hook result for $member: ${value.javaClass.name}")
        return value
    }

    inline fun <T> original(call: () -> T): T = try { call() } catch (error: InvocationTargetException) {
        throw error.targetException ?: error
    }
}
