package com.owo233.tcqt.hooks.base

import java.net.URL

class XpClassLoader(
    private val hostClassLoader: ClassLoader,
    private val ctxClassLoader: ClassLoader
) : ClassLoader() {

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return if (switchHostClass(name)) {
            hostClassLoader.loadClass(name)
        } else {
            ctxClassLoader.loadClass(name)
        }
    }

    override fun getResource(name: String): URL? {
        return ctxClassLoader.getResource(name) ?: hostClassLoader.getResource(name)
    }

    companion object {

        private val hostPackages = listOf(
            "androidx.lifecycle.",
            "com.qq.",
            "com.tencent",
            "mqq.",
            "oicq.",
            "tencent.im.",
        )

        fun switchHostClass(name: String): Boolean {
            for (pack in hostPackages) {
                if (name.startsWith(pack)) {
                    return true
                }
            }
            return false
        }
    }
}
