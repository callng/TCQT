package com.owo233.tcqt.hooks.base

class FixClassLoader(
    private val originalParent: ClassLoader,
    private val hostClassLoader: ClassLoader
) : ClassLoader(originalParent) {

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            super.loadClass(name, resolve)
        } catch (_: ClassNotFoundException) {
            hostClassLoader.loadClass(name)
        }
    }
}
