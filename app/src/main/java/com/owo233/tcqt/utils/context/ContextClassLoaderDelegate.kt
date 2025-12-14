package com.owo233.tcqt.utils.context

import android.content.Context
import com.owo233.tcqt.HookEnv

class ContextClassLoaderDelegate {

    private var patched: ClassLoader? = null

    fun getClassLoader(fallback: ClassLoader): ClassLoader {
        if (patched == null) {
            patched = XpSafeClassLoader(fallback)
        }
        return patched!!
    }

    private class XpSafeClassLoader(
        private val base: ClassLoader
    ) : ClassLoader(Context::class.java.classLoader) {

        override fun findClass(name: String): Class<*> {
            try {
                return Context::class.java.classLoader!!.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }

            if (name == "androidx.lifecycle.ReportFragment" ||
                name == "androidx.core.widget.NestedScrollView") {
                return HookEnv.hostClassLoader.loadClass(name)
            }

            return base.loadClass(name)
        }
    }
}
