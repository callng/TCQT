package com.owo233.tcqt.core.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class HookCallStateTest {
    private class Target { fun number(value: Int): Int = value; fun text(): String = "value" }
    private val method = Target::class.java.getDeclaredMethod("number", Int::class.javaPrimitiveType)

    @Test fun outcomesAreExclusiveAndFailuresRestoreEarlyReturn() {
        val param = HookCallState(method, null, arrayOf(1))
        assertNull(param.thisObject)
        val start = param.snapshot()
        param.result = 5
        val error = IllegalStateException("fixture")
        param.throwable = error
        assertNull(param.result)
        assertSame(error, assertFailsWith<IllegalStateException> { param.finish() })
        param.result = 7
        assertNull(param.throwable)
        assertEquals(7, param.finish())
        param.restore(start)
        assertEquals(false, param.isReturnEarly)
    }

    @Test fun primitiveNoOpIsTypedAndWrongTypesAreRejected() {
        assertEquals(0, HookContract.result(method, null))
        assertEquals(0, HookContract.result(method, Unit))
        assertFailsWith<ClassCastException> { HookContract.result(method, 1L) }
        assertFailsWith<ClassCastException> { HookContract.result(Target::class.java.getDeclaredMethod("text"), 4) }
    }

    @Test fun originalUnwrapsExceptionWithoutRetry() {
        val target = IllegalArgumentException("original")
        var calls = 0
        assertSame(target, assertFailsWith<IllegalArgumentException> {
            HookContract.original { calls++; throw java.lang.reflect.InvocationTargetException(target) }
        })
        assertEquals(1, calls)
    }

    @Test fun rejectsInvalidNativeArgumentShapesBeforeCallingJni() {
        HookContract.arguments(method, arrayOf(1))
        assertFailsWith<IllegalArgumentException> { HookContract.arguments(method, emptyArray()) }
        assertFailsWith<IllegalArgumentException> { HookContract.arguments(method, arrayOf(null)) }
        assertFailsWith<IllegalArgumentException> { HookContract.arguments(method, arrayOf("wrong")) }
    }
}
