package com.owo233.tcqt.ui.parasitic

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyInstrumentationTest {
    private fun Method.signature() = name + parameterTypes.joinToString { it.name }

    @Test
    fun everyPublicInstrumentationInstanceMethodHasADelegate() {
        val expected = Instrumentation::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
            .map { it.signature() }.toSet()
        val implemented = ProxyInstrumentation::class.java.declaredMethods.map { it.signature() }.toSet()
        assertEquals(emptySet(), expected - implemented, "Missing Instrumentation delegates")
    }

    @Test
    fun hiddenStartMethodsHaveExactFrameworkSignatures() {
        val prefix = arrayOf(Context::class.java, IBinder::class.java, IBinder::class.java)
        for (target in listOf(Activity::class.java, String::class.java)) {
            val method = ProxyInstrumentation::class.java.getDeclaredMethod(
                "execStartActivity", *prefix, target, Intent::class.java,
                Int::class.javaPrimitiveType!!, Bundle::class.java,
            )
            assertEquals(Instrumentation.ActivityResult::class.java, method.returnType)
            assertTrue(Modifier.isPublic(method.modifiers))
        }
        val asUser = ProxyInstrumentation::class.java.getDeclaredMethod(
            "execStartActivity", *prefix, String::class.java, Intent::class.java,
            Int::class.javaPrimitiveType!!, Bundle::class.java, UserHandle::class.java,
        )
        assertEquals(Instrumentation.ActivityResult::class.java, asUser.returnType)
        val batch = ProxyInstrumentation::class.java.getDeclaredMethod(
            "execStartActivities", *prefix, Activity::class.java, Array<Intent>::class.java, Bundle::class.java,
        )
        assertEquals(Void.TYPE, batch.returnType)
    }
}
