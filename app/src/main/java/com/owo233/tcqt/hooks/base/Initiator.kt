@file:JvmName("Initiator")
package com.owo233.tcqt.hooks.base

import com.owo233.tcqt.HookEnv

private val mClassCache: MutableMap<String, Class<*>?> = HashMap()

fun getSimpleName(className: String): String {
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

@JvmOverloads
fun load(className: String, classLoader: ClassLoader = HookEnv.hostClassLoader): Class<*>? {
    val name = getSimpleName(className)

    return try {
        if (classLoader == HookEnv.hostClassLoader && mClassCache.containsKey(name)) {
            mClassCache[name]
        } else {
            val clazz = classLoader.loadClass(name)
            mClassCache[name] = clazz
            clazz
        }
    } catch (_: ClassNotFoundException) {
        null
    }
}

@JvmOverloads
@Suppress("UNCHECKED_CAST")
fun <T> loadAs(
    className: String,
    classLoader: ClassLoader = HookEnv.hostClassLoader
): Class<T> = load(className, classLoader) as Class<T>

@JvmOverloads
fun loadOrThrow(
    className: String,
    classLoader: ClassLoader = HookEnv.hostClassLoader): Class<*> {
    return load(className, classLoader) ?: throw ClassNotFoundException(className)
}
