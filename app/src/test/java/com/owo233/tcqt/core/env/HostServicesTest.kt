package com.owo233.tcqt.core.env

import android.content.Context
import kotlin.test.*

class HostServicesTest {
    @Test fun missingAdapterFailsClearly() {
        val previous = HostServicesProvider.services
        try {
            HostServicesProvider.services = null
            assertFailsWith<IllegalStateException> { HostServicesProvider.requireServices() }
        } finally { HostServicesProvider.services = previous }
    }

    @Test fun environmentAndToastUseInstalledAdapter() {
        val previous = HostServicesProvider.services
        var toast: Pair<Int, String>? = null
        try {
            HostServicesProvider.services = object : HostServices {
                override fun isNightMode() = true
                override fun alternateVersionCode() = 42L
                override fun pluginClassLoader(context: Context, pluginName: String) = javaClass.classLoader!!
                override fun showToast(icon: Int, message: String) { toast = icon to message }
            }
            assertTrue(HookEnv.isNightMode())
            assertEquals(42L, HostServicesProvider.requireServices().alternateVersionCode())
            Toasts.success("saved")
            assertEquals(Toasts.ICON_SUCCESS to "saved", toast)
        } finally { HostServicesProvider.services = previous }
    }
}
