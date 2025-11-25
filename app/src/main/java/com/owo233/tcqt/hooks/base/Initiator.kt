@file:JvmName("Initiator")
package com.owo233.tcqt.hooks.base

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.utils.Log

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

    if (classLoader == HookEnv.hostClassLoader && mClassCache.containsKey(className)) {
        return mClassCache[name]
    }
    runCatching {
        val clazz = classLoader.loadClass(name)
        mClassCache[name] = clazz
        return clazz
    }.onFailure {
        // Log.e("load class $name failed", it)
    }
    return null
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
