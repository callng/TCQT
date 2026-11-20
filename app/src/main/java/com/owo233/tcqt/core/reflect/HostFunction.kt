package com.owo233.tcqt.core.reflect

import java.lang.reflect.Proxy

/** Pass Method.parameterTypes and use the returned Any with Method.invoke; module Kotlin types may be renamed by R8. */
internal fun hostFunction(type: Class<*>, callback: () -> Unit): Any =
    hostFunctionProxy(type, 0) { callback() }

@Suppress("UNCHECKED_CAST")
internal fun <T> hostFunction(type: Class<*>, callback: (T) -> Unit): Any =
    hostFunctionProxy(type, 1) { callback(it!![0] as T) }

@Suppress("UNCHECKED_CAST")
internal fun <T, U> hostFunction(type: Class<*>, callback: (T, U) -> Unit): Any =
    hostFunctionProxy(type, 2) { callback(it!![0] as T, it[1] as U) }

private fun hostFunctionProxy(type: Class<*>, arity: Int, callback: (Array<out Any?>?) -> Unit): Any {
    require(type.isInterface && type.name == "kotlin.jvm.functions.Function$arity") {
        "Expected host Function$arity, got ${type.name}"
    }
    val loader = type.classLoader!!
    val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
    return Proxy.newProxyInstance(loader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "invoke" -> { callback(args); unit }
            "toString" -> "TCQT ${type.name} callback"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args!![0]
            else -> error("Unexpected function method: $method")
        }
    }
}
