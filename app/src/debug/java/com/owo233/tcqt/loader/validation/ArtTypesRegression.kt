package com.owo233.tcqt.loader.validation

import androidx.annotation.Keep
import com.owo233.tcqt.core.hook.HookContract
import com.owo233.tcqt.core.hook.IHookEngine

@Keep
internal class ArtTypesTarget {
    fun booleanValue(v: Boolean) = !v
    fun byteValue(v: Byte) = (v + 1).toByte()
    fun charValue(v: Char) = v + 1
    fun shortValue(v: Short) = (v + 1).toShort()
    fun intValue(v: Int) = v + 1
    fun longValue(v: Long) = v + 1
    fun floatValue(v: Float) = v + 1
    fun doubleValue(v: Double) = v + 1
    fun objectValue(v: String) = "$v!"
    fun voidFailure() { throw IllegalArgumentException("void-fixture") }
    fun objectFailure(): String = throw IllegalArgumentException("object-fixture")
    companion object {
        @JvmStatic fun staticBooleanValue(v: Boolean) = !v
        @JvmStatic fun staticByteValue(v: Byte) = (v + 1).toByte()
        @JvmStatic fun staticCharValue(v: Char) = v + 1
        @JvmStatic fun staticShortValue(v: Short) = (v + 1).toShort()
        @JvmStatic fun staticIntValue(v: Int) = v + 1
        @JvmStatic fun staticLongValue(v: Long) = v + 1
        @JvmStatic fun staticFloatValue(v: Float) = v + 1
        @JvmStatic fun staticDoubleValue(v: Double) = v + 1
        @JvmStatic fun staticObjectValue(v: String) = "$v!"
        @JvmStatic fun staticVoidFailure() { throw IllegalArgumentException("void-fixture") }
        @JvmStatic fun staticObjectFailure(): String = throw IllegalArgumentException("object-fixture")
    }
}

internal object ArtTypesRegression {
    private data class Case(val name: String, val type: Class<*>, val input: Any, val expected: Any)
    fun run(engine: IHookEngine): Int {
        val target = ArtTypesTarget()
        val cases = listOf(
            Case("booleanValue", Boolean::class.javaPrimitiveType!!, true, false),
            Case("byteValue", Byte::class.javaPrimitiveType!!, 2.toByte(), 3.toByte()),
            Case("charValue", Char::class.javaPrimitiveType!!, 'a', 'b'),
            Case("shortValue", Short::class.javaPrimitiveType!!, 2.toShort(), 3.toShort()),
            Case("intValue", Int::class.javaPrimitiveType!!, 2, 3),
            Case("longValue", Long::class.javaPrimitiveType!!, 2L, 3L),
            Case("floatValue", Float::class.javaPrimitiveType!!, 2.25f, 3.25f),
            Case("doubleValue", Double::class.javaPrimitiveType!!, 2.25, 3.25),
            Case("objectValue", String::class.java, "fixture", "fixture!")
        )
        var checks = 0
        for (isStatic in listOf(false, true)) {
            fun methodName(name: String) = if (isStatic) "static" + name.replaceFirstChar { it.uppercase() } else name
            val receiver = if (isStatic) null else target
            for (case in cases) {
                val method = target.javaClass.getDeclaredMethod(methodName(case.name), case.type)
                val handle = engine.hookReplace(method) { it.proceed() }
                try {
                    check(HookContract.original { method.invoke(receiver, case.input) } == case.expected) { method.name }
                    check(engine.getInvoker(method).invokeOrigin(receiver, case.input) == case.expected) { method.name }
                } finally { handle.unhook() }
                check(HookContract.original { method.invoke(receiver, case.input) } == case.expected) { "restore ${method.name}" }
                checks++
            }
            for ((name, error) in listOf("voidFailure" to "void-fixture", "objectFailure" to "object-fixture")) {
                val method = target.javaClass.getDeclaredMethod(methodName(name))
                val handle = engine.hookReplace(method) { it.proceed() }
                try {
                    check(runCatching { HookContract.original { method.invoke(receiver) } }.exceptionOrNull()?.message == error)
                } finally { handle.unhook() }
                checks++
            }
        }
        return checks
    }
}
