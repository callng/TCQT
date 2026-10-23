package com.owo233.tcqt.host.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookInstallationRegistryTest {

    private class FirstOwner
    private class SecondOwner

    @Test
    fun installsEachHookOnlyOncePerSessionClass() {
        val registry = HookInstallationRegistry()
        var attempts = 0

        val pushHook = HookInstallationRegistry.Hook.MSF_PUSH
        val backgroundHook = HookInstallationRegistry.Hook.BACKGROUND
        assertTrue(registry.installIfMissing(FirstOwner::class.java, pushHook) { attempts++ })
        assertFalse(registry.installIfMissing(FirstOwner::class.java, pushHook) { attempts++ })
        assertTrue(registry.installIfMissing(FirstOwner::class.java, backgroundHook) { attempts++ })
        assertTrue(registry.installIfMissing(SecondOwner::class.java, pushHook) { attempts++ })

        assertEquals(3, attempts)
    }

    @Test
    fun failedInstallationCanBeRetried() {
        val registry = HookInstallationRegistry()
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            registry.installIfMissing(FirstOwner::class.java, HookInstallationRegistry.Hook.MSF_PUSH) {
                attempts++
                error("hook install failed")
            }
        }
        assertTrue(
            registry.installIfMissing(FirstOwner::class.java, HookInstallationRegistry.Hook.MSF_PUSH) { attempts++ }
        )

        assertEquals(2, attempts)
    }
}
