package com.owo233.tcqt.ext

object XpClassLoader: ClassLoader() {
    lateinit var hostClassLoader: ClassLoader
    lateinit var ctxClassLoader: ClassLoader

    fun load(name: String): Class<*>? {
        return runCatching {
            loadClass(name)
        }.getOrNull()
    }

    private fun getSimpleName(className: String): String {
        var name = className
        if (name.startsWith('L') && name.endsWith(';') || name.contains('/')) {
            var flag = 0
            if (name.startsWith('L')) {
                flag = flag or (1 shl 1)
            }
            if (name.endsWith(';')) {
                flag = flag or 1
            }
            if (flag > 0) {
                name = name.substring(flag shr 1, name.length - (flag and 1))
            }
            name = name.replace('/', '.')
        }
        return name
    }

    override fun loadClass(className: String): Class<*>? {
        val name = getSimpleName(className)
        return runCatching {
            hostClassLoader.loadClass(name)
        }.getOrElse {
            runCatching {
                ctxClassLoader.loadClass(name)
            }.getOrElse {
                super.loadClass(name)
            }
        }
    }
}
