package com.owo233.tcqt.foundation.utils.reflect

import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.features.hooks.base.load
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ClassUtils {

    /**
     * 默认字典：a..z
     * 可以通过 Finder.dictionary(...) 覆盖
     */
    private val DEFAULT_DIC: List<String> = ('a'..'z').map { it.toString() }

    /** 类名拼接策略 */
    enum class JoinStyle { DOT, DOLLAR }

    /** 扫描结构策略 */
    enum class ScanMode {
        /** 只扫 prefix + join + token */
        DIRECT,
        /** 先扫 outer（prefix.join.outerToken），每个 outer 再扫 inner（$innerToken） */
        OUTER_THEN_INNER
    }

    /**
     * 用于加载 Class 的函数类型：
     * - XP 环境通常用 hostClassLoader.loadClass
     * - 普通环境用 Class.forName
     */
    fun interface ClassLoaderFn {
        fun load(name: String): Class<*>?
    }

    private var defaultLoader: ClassLoaderFn = ClassLoaderFn { name ->
        runCatching { load(name, HookEnv.hostClassLoader) }.getOrNull()
    }

    fun create(prefix: String): Finder = Finder(prefix)

    class Finder internal constructor(
        private val prefix: String
    ) {
        private var dic: List<String> = DEFAULT_DIC
        private var joinStyle: JoinStyle = JoinStyle.DOT
        private var scanMode: ScanMode = ScanMode.DIRECT
        private var loader: ClassLoaderFn = defaultLoader

        private var useCache: Boolean = true

        private var classPredicate: ((Class<*>) -> Boolean)? = null
        private var methodPredicate: ((Class<*>, Method) -> Boolean)? = null

        /** 是否允许命中后继续（默认 false：找到第一个就返回） */
        private var findAll: Boolean = false

        fun dictionary(tokens: List<String>) = apply { this.dic = tokens }

        /** 便捷：自定义字典生成器 */
        fun dictionary(build: MutableList<String>.() -> Unit) = apply {
            val list = mutableListOf<String>()
            list.build()
            this.dic = list
        }

        fun join(style: JoinStyle) = apply { this.joinStyle = style }

        /**
         * DIRECT：prefix + (DOT|DOLLAR) + token
         * OUTER_THEN_INNER：prefix.DOT.outerToken + "$" + innerToken
         */
        fun scan(mode: ScanMode) = apply { this.scanMode = mode }

        fun loader(fn: ClassLoaderFn) = apply { this.loader = fn }

        fun cache(enable: Boolean = true) = apply { this.useCache = enable }

        /** 类级别判定（只要 classPredicate 命中即认为 class 命中） */
        fun whereClass(check: (Class<*>) -> Boolean) = apply { this.classPredicate = check }

        /**
         * 方法级别判定：
         * - 扫描 class 时遍历 declaredMethods，只要有一个 method 满足即命中该 class（或返回该 method）
         */
        fun whereMethod(check: (Class<*>, Method) -> Boolean) = apply { this.methodPredicate = check }

        /** 找第一个匹配的 Class */
        fun findFirstClass(): Class<*>? {
            this.findAll = false
            return findClassesInternal().firstOrNull()
        }

        /** 找所有匹配的 Class */
        fun findAllClasses(): List<Class<*>> {
            this.findAll = true
            return findClassesInternal()
        }

        /** 找第一个匹配的方法（会返回 Method；你也能从 method.declaringClass 拿 class） */
        fun findFirstMethod(): Method? {
            this.findAll = false
            return findMethodsInternal().firstOrNull()
        }

        /** 找所有匹配的方法 */
        fun findAllMethods(): List<Method> {
            this.findAll = true
            return findMethodsInternal()
        }

        // -------- 内部实现 --------

        private fun findClassesInternal(): List<Class<*>> {
            val results = ArrayList<Class<*>>(8)

            for (className in sequenceClassNames()) {
                val c = loadClass(className) ?: continue

                val classOk = classPredicate?.invoke(c) ?: false
                if (classOk) {
                    results.add(c)
                    if (!findAll) return results
                    continue
                }

                val mp = methodPredicate
                if (mp != null) {
                    for (m in c.declaredMethods) {
                        if (mp.invoke(c, m)) {
                            results.add(c)
                            if (!findAll) return results
                            break
                        }
                    }
                }
            }

            return results
        }

        private fun findMethodsInternal(): List<Method> {
            val mp = methodPredicate ?: return emptyList()
            val results = ArrayList<Method>(8)

            for (className in sequenceClassNames()) {
                val c = loadClass(className) ?: continue
                for (m in c.declaredMethods) {
                    if (mp.invoke(c, m)) {
                        makeAccessible(m)
                        results.add(m)
                        if (!findAll) return results
                    }
                }
            }

            return results
        }

        private fun sequenceClassNames(): Sequence<String> = sequence {
            when (scanMode) {
                ScanMode.DIRECT -> {
                    for (t in dic) yield(joinName(prefix, t, joinStyle))
                }
                ScanMode.OUTER_THEN_INNER -> {
                    for (outer in dic) {
                        val outerName = "$prefix.$outer"
                        yield(outerName)

                        for (inner in dic) {
                            val innerName = "$outerName$$inner"
                            yield(innerName)
                        }
                    }
                }
            }
        }

        private fun joinName(prefix: String, token: String, style: JoinStyle): String {
            return when (style) {
                JoinStyle.DOT -> "$prefix.$token"
                JoinStyle.DOLLAR -> "$prefix$$token"
            }
        }

        private fun loadClass(name: String): Class<*>? {
            if (!useCache) return loader.load(name)

            val key = cacheKey(name)
            ClassCache[key]?.let { return it }

            val c = loader.load(name) ?: return null
            ClassCache[key] = c
            return c
        }

        private fun cacheKey(className: String): String {
            return className
        }

        private fun makeAccessible(m: Method) {
            runCatching {
                if (!m.isAccessible) m.isAccessible = true
            }
        }
    }

    private val ClassCache = ConcurrentHashMap<String, Class<*>>()

    fun clearCache() = ClassCache.clear()
}
