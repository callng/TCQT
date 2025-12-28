package com.owo233.tcqt.utils.reflect

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object MethodUtils {

    fun create(clazz: Class<*>): Finder = Finder(clazz)

    enum class AccessModifier {
        ANY, PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE
    }

    class Finder internal constructor(
        private val clazz: Class<*>
    ) {
        private var methodName: String? = null
        private var returnType: Class<*>? = null

        private var paramTypes: Array<Class<*>?>? = null
        private var paramCount: Int? = null

        private var accessFilter: AccessModifier = AccessModifier.ANY

        /** 递归父类层级查找（默认 false） */
        private var recursive: Boolean = false

        /** 是否包含 java.lang.Object 的方法（默认 false） */
        private var includeObjectMethods: Boolean = false

        /**
         * 兜底：ClassLoader 不同导致 isAssignableFrom 失效时，按类名匹配
         * （例如两个同名接口/Function1/Unit 来自不同 loader）
         */
        private var matchTypeByNameFallback: Boolean = true

        fun named(name: String) = apply { methodName = name }

        fun returns(type: Class<*>) = apply { returnType = type }

        inline fun <reified T> returns() = returns(T::class.java)

        /**
         * 参数类型匹配：
         * - 允许 null 作为通配符（该位置不约束）
         * - 会自动设置 paramCount
         */
        fun params(vararg types: Class<*>?) = apply {
            paramTypes = arrayOf(*types)
            paramCount = types.size
        }

        fun paramCount(count: Int) = apply { paramCount = count.coerceAtLeast(0) }

        fun access(mod: AccessModifier) = apply { accessFilter = mod }

        fun recursive(enable: Boolean = true) = apply { recursive = enable }

        fun includeObjectMethods(enable: Boolean = true) = apply { includeObjectMethods = enable }

        fun sameNameTypeMatch(enable: Boolean = true) = apply { matchTypeByNameFallback = enable }

        /** 找到最佳匹配的一个方法；找不到返回 null */
        fun findOrNull(): Method? = runCatching { findOrThrow() }.getOrNull()

        /** 找到最佳匹配的一个方法；找不到直接抛错 */
        fun findOrThrow(): Method {
            val key = cacheKey()
            if (key != null) {
                MethodCache[key]?.let { return makeAccessible(it) }
            }

            val methods = findAllInternal()
            if (methods.isEmpty()) error("No matching method. name=$methodName return=${returnType?.name} class=${clazz.name}")

            // 优先 exact match，再取 score 最高的那个（列表已按 score 排序）
            val exact = methods.firstOrNull { isExactMatch(it) }
            val best = exact ?: methods.first()

            if (key != null) MethodCache[key] = best
            return makeAccessible(best)
        }

        /** 找到所有候选方法（已按匹配度排序，最佳在前） */
        fun findAll(): List<Method> = findAllInternal().map { makeAccessible(it) }

        fun invokeStatic(vararg args: Any?): Any? {
            val m = findOrThrow()
            require(Modifier.isStatic(m.modifiers)) { "Method is not static: ${m.declaringClass.name}#${m.name}" }
            return m.invoke(null, *args)
        }

        fun invokeOn(instance: Any, vararg args: Any?): Any? {
            val m = findOrThrow()
            require(!Modifier.isStatic(m.modifiers)) { "Method is static: ${m.declaringClass.name}#${m.name}" }
            return m.invoke(instance, *args)
        }

        // -------- 内部实现 --------

        private fun findAllInternal(): List<Method> {
            val candidates = ArrayList<Method>(8)

            fun scan(c: Class<*>) {
                for (m in c.declaredMethods) {
                    if (!includeObjectMethods && c == Any::class.java) continue

                    if (!passAccess(m)) continue
                    if (methodName != null && m.name != methodName) continue
                    if (returnType != null && !isTypeSame(returnType!!, m.returnType)) continue

                    val mp = m.parameterTypes

                    if (paramCount != null && mp.size != paramCount) continue

                    if (paramTypes != null) {
                        val wanted = paramTypes!!
                        if (mp.size != wanted.size) continue

                        var ok = true
                        for (i in wanted.indices) {
                            val w = wanted[i] ?: continue // wildcard
                            val actual = mp[i]
                            // 语义：方法参数类型 actual 必须能接收 w（w 是期望传入的类型）
                            if (!isParamCompatible(actual, w)) {
                                ok = false
                                break
                            }
                        }
                        if (!ok) continue
                    }

                    candidates.add(m)
                }
            }

            // 当前类
            scan(clazz)

            // 父类链
            if (recursive) {
                var c: Class<*>? = clazz.superclass
                while (c != null && c != Any::class.java) {
                    scan(c)
                    c = c.superclass
                }
                if (includeObjectMethods) scan(Any::class.java)
            }

            // 按匹配度排序：score 降序，其次 name / paramCount
            candidates.sortWith(
                compareByDescending<Method> { calculateMatchScore(it) }
                    .thenBy { it.name }
                    .thenBy { it.parameterCount }
            )

            return candidates
        }

        private fun passAccess(m: Method): Boolean {
            if (accessFilter == AccessModifier.ANY) return true
            return getAccessModifier(m) == accessFilter
        }

        private fun makeAccessible(m: Method): Method {
            if (!Modifier.isPublic(m.modifiers) || !Modifier.isPublic(m.declaringClass.modifiers)) {
                m.isAccessible = true
            }
            return m
        }

        private fun getAccessModifier(m: Method): AccessModifier {
            val mod = m.modifiers
            return when {
                Modifier.isPrivate(mod) -> AccessModifier.PRIVATE
                Modifier.isProtected(mod) -> AccessModifier.PROTECTED
                Modifier.isPublic(mod) -> AccessModifier.PUBLIC
                else -> AccessModifier.PACKAGE_PRIVATE
            }
        }

        /**
         * exact match：每个位置参数 Class 必须完全相等（不考虑继承）
         */
        private fun isExactMatch(m: Method): Boolean {
            val wanted = paramTypes ?: return false
            val actual = m.parameterTypes
            if (wanted.size != actual.size) return false
            for (i in wanted.indices) {
                val w = wanted[i] ?: return false // exact 不允许 wildcard
                if (w != actual[i]) return false
            }
            return true
        }

        private fun calculateMatchScore(m: Method): Int {
            var score = 0

            if (isExactMatch(m)) score += 100

            val wanted = paramTypes
            if (wanted != null) {
                val actual = m.parameterTypes
                for (i in wanted.indices) {
                    val w = wanted[i] ?: continue
                    val a = actual[i]

                    if (w == a) {
                        score += 10
                    } else if (isParamCompatible(a, w)) {
                        // w 是更具体的类型，a 更泛；越贴近越高分
                        val depth = inheritanceDepth(w, a)
                        if (depth >= 0) score += (10 - depth).coerceAtLeast(1)
                    }
                }
            }

            // 名称完全匹配给一点权重（通常会指定 named）
            if (methodName != null && m.name == methodName) score += 5

            // 返回值匹配
            if (returnType != null && isTypeSame(returnType!!, m.returnType)) score += 5

            return score
        }

        /**
         * 参数兼容性判断：
         * - 常规：actual.isAssignableFrom(wanted)
         * - 兜底：类名相等（跨 loader）
         */
        private fun isParamCompatible(actualParam: Class<*>, wantedArg: Class<*>): Boolean {
            if (actualParam.isAssignableFrom(wantedArg)) return true
            if (matchTypeByNameFallback && actualParam.name == wantedArg.name) return true
            return false
        }

        /**
         * 类型“相等”判断（用于 returnType）：允许按类名兜底
         */
        private fun isTypeSame(expected: Class<*>, actual: Class<*>): Boolean {
            if (expected == actual) return true
            if (matchTypeByNameFallback && expected.name == actual.name) return true
            return false
        }

        /**
         * child 到 parent 的继承深度（child==parent => 0）
         * 不可达返回 -1
         */
        private fun inheritanceDepth(child: Class<*>, parent: Class<*>): Int {
            if (child == parent) return 0
            var depth = 0
            var cur: Class<*>? = child
            while (cur != null && cur != parent) {
                depth++
                cur = cur.superclass
            }
            return if (cur == parent) depth else -1
        }

        private fun cacheKey(): String? {
            // 如果没有任何条件，缓存没意义
            if (methodName == null && returnType == null && paramTypes == null && paramCount == null && accessFilter == AccessModifier.ANY && !recursive) {
                return null
            }

            val p = paramTypes?.joinToString(",") { it?.name ?: "*" } ?: "<no_params>"
            val pc = paramCount?.toString() ?: "<no_count>"

            return buildString {
                append(clazz.name).append('#')
                append(methodName ?: "<no_name>").append('|')
                append(returnType?.name ?: "<no_ret>").append('|')
                append("pc=").append(pc).append('|')
                append("p=").append(p).append('|')
                append("acc=").append(accessFilter.name).append('|')
                append("rec=").append(recursive).append('|')
                append("obj=").append(includeObjectMethods).append('|')
                append("fb=").append(matchTypeByNameFallback)
            }
        }
    }

    private val MethodCache = ConcurrentHashMap<String, Method>()

    fun clearCache() = MethodCache.clear()
}
