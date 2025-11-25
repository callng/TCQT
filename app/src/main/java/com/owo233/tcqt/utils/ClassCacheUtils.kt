package com.owo233.tcqt.utils

import com.owo233.tcqt.hooks.base.load
import java.util.concurrent.ConcurrentHashMap

object ClassCacheUtils {

    private val classCache = ConcurrentHashMap<String, Class<*>>()

    class FinderBuilder {
        internal val classNames = mutableListOf<String>()
        internal var indices: IntArray = intArrayOf()

        fun candidates(vararg names: String) {
            classNames += names
        }

        fun syntheticIndex(vararg ids: Int) {
            indices = ids
        }
    }

    fun findClass(init: FinderBuilder.() -> Unit): Class<*>? {
        val builder = FinderBuilder().apply(init)
        val candidates = builder.classNames
        if (candidates.isEmpty()) return null

        val cacheKey = candidates.first()
        classCache[cacheKey]?.let { return it }

        return candidates.firstNotNullOfOrNull { className ->
            findClassWithSyntheticsImpl(className, *builder.indices)
        }?.also { clazz ->
            classCache[cacheKey] = clazz
            classCache[clazz.name] = clazz
        }
    }

    private fun findClassWithSyntheticsImpl(
        className: String,
        vararg indices: Int
    ): Class<*>? {
        load(className)?.let { return it }

        for (i in indices) {
            val syntheticName = "$className$$i"
            val syntheticClass = load(syntheticName) ?: continue

            val outerClass = runCatching {
                syntheticClass.getDeclaredField("this$0").type
            }.getOrNull()

            if (outerClass != null) return outerClass
        }
        return null
    }
}
