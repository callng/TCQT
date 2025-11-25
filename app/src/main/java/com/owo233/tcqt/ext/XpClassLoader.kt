package com.owo233.tcqt.ext

import java.util.concurrent.ConcurrentHashMap

class XpClassLoader private constructor() : ClassLoader() {

    lateinit var ctxClassLoader: ClassLoader
    lateinit var hostClassLoader: ClassLoader
    private val classCache = ConcurrentHashMap<String, Class<*>>(128)

    fun load(name: String): Class<*>? {
        classCache[name]?.let { return it }
        val ret = loadClass(name)?.also { loadedClass ->
            classCache[name] = loadedClass
        }
        return ret
    }

    fun loadOrThrow(name: String): Class<*> {
        return load(name) ?: throw ClassNotFoundException(name)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> loadAs(name: String): Class<T> = load(name) as Class<T>

    private fun getSimpleName(className: String): String {
        var name = className
        if (name.startsWith('L') && name.endsWith(';') || name.contains('/')) {
            var flag = 0
            if (name.startsWith('L')) flag = flag or (1 shl 1)
            if (name.endsWith(';')) flag = flag or 1
            if (flag > 0) name = name.substring(flag shr 1, name.length - (flag and 1))
            name = name.replace('/', '.')
        }
        return name
    }

    override fun loadClass(className: String): Class<*>? {
        val name = getSimpleName(className)
        return runCatching { hostClassLoader.loadClass(name) }
            .getOrElse {
                runCatching { ctxClassLoader.loadClass(name) }
                    .getOrElse { runCatching { super.loadClass(name) }.getOrNull() }
            }
    }

    companion object {

        val INSTANCE = XpClassLoader()

        fun init(ctx: ClassLoader, host: ClassLoader) {
            INSTANCE.ctxClassLoader = ctx
            INSTANCE.hostClassLoader = host
        }

        fun load(name: String) = INSTANCE.load(name)
        fun loadOrThrow(name: String) = INSTANCE.loadOrThrow(name)
        fun <T> loadAs(name: String) = INSTANCE.loadAs<T>(name)
    }
}
