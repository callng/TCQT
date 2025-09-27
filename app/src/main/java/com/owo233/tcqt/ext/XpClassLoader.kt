package com.owo233.tcqt.ext

import com.owo233.tcqt.hooks.base.moduleClassLoader
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.field

object XpClassLoader: ClassLoader() {
    lateinit var hostClassLoader: ClassLoader
    lateinit var ctxClassLoader: ClassLoader

    fun load(name: String): Class<*>? {
        return runCatching {
            loadClass(name)
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> loadAs(name: String): Class<T> = load(name) as Class<T>

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

    fun injectClassloader(): Boolean {
        val moduleLoader = moduleClassLoader
        if (runCatching { moduleLoader.loadClass("mqq.app.MobileQQ") }.isSuccess) return true

        val parent = moduleLoader.parent
        val field = ClassLoader::class.java.field("parent")!!

        field.set(XpClassLoader, parent)

        if (load("mqq.app.MobileQQ") == null) {
            Log.e("XpClassLoader inject failed.")
            return false
        }

        field.set(moduleLoader, XpClassLoader)

        return runCatching {
            Class.forName("mqq.app.MobileQQ")
        }.onFailure {
            Log.e("Classloader inject failed.")
        }.isSuccess
    }
}
