package com.owo233.tcqt.core.hook

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class HookLifecycleTest {
    private class Callback(val priority: Int)
    private val order = compareByDescending<Callback> { it.priority }

    @Test fun publishesBeforeNativeInstallAndKeepsRetiredBackup() {
        lateinit var registry: HookLifecycle<String, Callback, String>
        var id = 0L
        var removals = 0
        registry = HookLifecycle({ "backup-$it" }, { slot ->
            id = slot.id
            assertSame(slot, registry.forId(id))
            assertEquals(1, slot.callbacks.size)
            true
        }, { removals++; true }, order)
        val handle = assertNotNull(registry.register("method", Callback(50)))
        handle.unhook(); handle.unhook()
        assertEquals(1, removals)
        assertNull(registry.forKey("method"))
        assertEquals("backup-method", registry.forId(id)?.value)
        assertEquals(emptyList(), registry.forId(id)?.callbacks)
    }

    @Test fun failedInstallRollsBackAndFailedUninstallCanBeRetried() {
        var installs = 0
        var remove = false
        val registry = HookLifecycle<String, Callback, String>({ it }, { ++installs > 1 }, { remove }, order)
        assertNull(registry.register("method", Callback(1)))
        assertNull(registry.forKey("method"))
        val first = assertNotNull(registry.register("method", Callback(1)))
        first.unhook()
        assertNotNull(registry.forKey("method"))
        val second = assertNotNull(registry.register("method", Callback(2)))
        assertEquals(2, installs)
        remove = true
        second.unhook()
        assertNull(registry.forKey("method"))
    }

    @Test fun reentrantRegistrationUsesThePublishedSlot() {
        lateinit var registry: HookLifecycle<String, Callback, String>
        var installs = 0
        registry = HookLifecycle({ it }, { slot ->
            installs++
            registry.register(slot.key, Callback(90))
            true
        }, { true }, order)
        registry.register("method", Callback(10))
        assertEquals(1, installs)
        assertEquals(listOf(90, 10), registry.forKey("method")!!.callbacks.map { it.priority })
    }

    @Test fun concurrentRegistrationsAndRemovalsLoseNoCallbacks() {
        var installs = 0
        var uninstalls = 0
        val registry = HookLifecycle<String, Callback, String>({ it }, { installs++; true }, { uninstalls++; true }, order)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val handles = (0 until 80).map { pool.submit<Unhook> { registry.register("method", Callback(it))!! } }
                .map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(80, registry.forKey("method")!!.callbacks.size)
            handles.map { pool.submit { it.unhook() } }.forEach { it.get(5, TimeUnit.SECONDS) }
            assertNull(registry.forKey("method"))
            assertEquals(1, installs)
            assertEquals(1, uninstalls)
        } finally { pool.shutdownNow() }
    }
}
