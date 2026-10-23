package com.owo233.tcqt.host.service

import java.util.WeakHashMap

internal class HookInstallationRegistry {

    enum class Hook {
        MSF_PUSH,
        BACKGROUND,
    }

    private val installed = WeakHashMap<Class<*>, MutableSet<Hook>>()

    @Synchronized
    fun installIfMissing(owner: Class<*>, hook: Hook, install: () -> Unit): Boolean {
        if (installed[owner]?.contains(hook) == true) return false

        install()
        installed.getOrPut(owner) { mutableSetOf() }.add(hook)
        return true
    }
}
