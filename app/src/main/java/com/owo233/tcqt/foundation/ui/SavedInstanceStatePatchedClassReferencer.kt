package com.owo233.tcqt.foundation.ui

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv

class SavedInstanceStatePatchedClassReferencer(
    private val baseReferencer: ClassLoader
) : ClassLoader(Context::class.java.classLoader) {

    private val hostReferencer: ClassLoader = HookEnv.hostClassLoader

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        try {
            return parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
        }

        if (name == "androidx.lifecycle.ReportFragment") {
            try {
                return hostReferencer.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        return baseReferencer.loadClass(name)
    }
}
