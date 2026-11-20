package com.owo233.tcqt.core.reflect

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HostFunctionTest {
    // Give the host Kotlin types a distinct identity, as QQ and a minified module have.
    private val loader = object : ClassLoader(HostFunctionTest::class.java.classLoader) {
        private val isolated = setOf("kotlin.Function", "kotlin.Unit",
            "kotlin.jvm.functions.Function0", "kotlin.jvm.functions.Function1", "kotlin.jvm.functions.Function2")

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name !in isolated) return super.loadClass(name, resolve)
            return findLoadedClass(name) ?: run {
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                defineClass(name, bytes, 0, bytes.size)
            }
        }
    }

    private fun type(arity: Int) = loader.loadClass("kotlin.jvm.functions.Function$arity")
    private val unit get() = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)

    private fun invoke(arity: Int, callback: Any, vararg args: Any?): Any? =
        type(arity).getMethod("invoke", *Array(arity) { Any::class.java }).invoke(callback, *args)

    @Test
    fun function0UsesHostInterfaceAndReturnsHostUnit() {
        var calls = 0
        val callback = hostFunction(type(0)) { -> calls++ }
        assertTrue(type(0).isInstance(callback))
        assertNotSame(Function0::class.java, type(0))
        assertNotSame(Unit, unit)
        assertSame(unit, invoke(0, callback))
        assertEquals(1, calls)
    }

    @Test
    fun function1ForwardsNullableArgument() {
        val results = mutableListOf<String?>()
        val callback = hostFunction<String?>(type(1)) { results += it }
        assertSame(unit, invoke(1, callback, "nick"))
        assertSame(unit, invoke(1, callback, null))
        assertEquals(listOf("nick", null), results)
    }

    @Test
    fun function2PreservesSuccessAndFailureResults() {
        val results = mutableListOf<Pair<Int, String>>()
        val callback = hostFunction<Int, String>(type(2)) { code, message -> results += code to message }
        assertSame(unit, invoke(2, callback, 0, "ok"))
        assertSame(unit, invoke(2, callback, 17, "failed"))
        assertEquals(listOf(0 to "ok", 17 to "failed"), results)
    }

    @Test
    fun objectMethodsDoNotExecuteCallback() {
        var calls = 0
        val callback = hostFunction(type(0)) { -> calls++ }
        assertTrue(callback.toString().contains("Function0"))
        assertEquals(System.identityHashCode(callback), callback.hashCode())
        assertTrue(callback == callback)
        assertEquals(false, callback.equals(null))
        assertEquals(false, callback == Any())
        assertEquals(0, calls)
    }

    @Test
    fun rejectsWrongArityAndPreservesCallbackFailure() {
        assertFailsWith<IllegalArgumentException> { hostFunction(type(1)) { -> } }
        val failure = IllegalStateException("callback failed")
        val callback = hostFunction(type(0)) { -> throw failure }
        assertSame(failure, assertFailsWith<InvocationTargetException> { invoke(0, callback) }.cause)
    }
}
