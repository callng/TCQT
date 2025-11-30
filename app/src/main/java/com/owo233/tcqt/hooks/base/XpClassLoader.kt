package com.owo233.tcqt.hooks.base

import java.net.URL
import java.util.Enumeration

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

    override fun getResources(name: String): Enumeration<URL> {
        val ctxResources = ctxClassLoader.getResources(name)
        val hostResources = hostClassLoader.getResources(name)

        return object : Enumeration<URL> {
            private var current = ctxResources
            private var useHost = false

            override fun hasMoreElements(): Boolean {
                if (current.hasMoreElements()) {
                    return true
                }
                if (!useHost) {
                    current = hostResources
                    useHost = true
                    return current.hasMoreElements()
                }
                return false
            }

            override fun nextElement(): URL {
                if (!hasMoreElements()) {
                    throw NoSuchElementException()
                }
                return current.nextElement()
            }
        }
    }

    companion object {

        private val hostPackages = listOf(
            "androidx.lifecycle.",
            "com.qq.",
            "com.qzone.",
            "com.tencent",
            "mqq.",
            "oicq.",
            "tencent.im."
        )

        private fun switchHostClass(name: String): Boolean {
            for (pack in hostPackages) {
                if (name.startsWith(pack)) {
                    return true
                }
            }
            return false
        }
    }
}
