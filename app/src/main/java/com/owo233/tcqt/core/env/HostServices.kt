package com.owo233.tcqt.core.env

import android.content.Context

/** Host-specific capabilities installed by the loader after class-loader capture. */
interface HostServices {
    fun isNightMode(): Boolean
    fun pluginClassLoader(context: Context, pluginName: String): ClassLoader
    fun alternateVersionCode(): Long
    fun showToast(icon: Int, message: String)
}

internal object HostServicesProvider {
    @Volatile var services: HostServices? = null
    fun requireServices(): HostServices = checkNotNull(services) { "Host services have not been installed" }
}
