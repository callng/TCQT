package com.owo233.tcqt.foundation.utils.reflect

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object FieldUtils {

    fun create(target: Any): Finder = Finder(Target.Instance(target))

    fun create(clazz: Class<*>): Finder = Finder(Target.StaticClass(clazz))

    class Finder internal constructor(
        private val target: Target
    ) {
        private var fieldName: String? = null
        private var fieldType: Class<*>? = null

        private var parentClass: Class<*>? = null
        private var recursive: Boolean = false

        /** 当按类型匹配多个字段时，取第几个（默认 0） */
        private var index: Int = 0

        /** 按类型搜索时，是否优先返回实例字段（默认 true） */
        private var preferInstance: Boolean = true

        /**
         * 兜底：当 ClassLoader 不同导致 isAssignableFrom 失效时，
         * 可以按类名匹配字段类型（例如两个 kotlin.Unit 或同名接口来自不同 loader）
         */
        private var matchTypeByNameFallback: Boolean = true

        fun named(name: String) = apply { this.fieldName = name }

        fun typed(type: Class<*>) = typedInternal(type)

        @PublishedApi
        internal fun typedInternal(type: Class<*>) = apply { this.fieldType = type }

        inline fun <reified T> typed() = typedInternal(T::class.java)

        /**
         * 指定优先查找的“某个父类层级”。
         * 仍然可以配合 recursive(true) 做更广泛搜索。
         */
        fun inParent(parent: Class<*>) = apply { this.parentClass = parent }

        /** 是否递归遍历父类层级查找（默认 false） */
        fun recursive(enable: Boolean = true) = apply { this.recursive = enable }

        /** 选择第 n 个匹配项（按字段声明顺序） */
        fun index(i: Int) = apply { this.index = i.coerceAtLeast(0) }

        /** 按类型搜索时是否优先实例字段 */
        fun preferInstance(enable: Boolean = true) = apply { this.preferInstance = enable }

        /** 是否启用“类型按类名匹配”的兜底 */
        fun sameNameTypeMatch(enable: Boolean = true) = apply { this.matchTypeByNameFallback = enable }

        fun getValue(): Any? = findField()?.let { get(it) }

        fun getOrNull(): Any? = runCatching { getValue() }.getOrNull()

        fun getOrThrow(): Any =
            getValue() ?: error("Field not found. name=$fieldName type=${fieldType?.name} target=${target.targetClass().name}")

        fun setValue(value: Any?) {
            findField()?.let { set(it, value) }
        }

        fun setOrThrow(value: Any?) {
            val f = findField()
                ?: error("Field not found. name=$fieldName type=${fieldType?.name} target=${target.targetClass().name}")
            set(f, value)
        }

        fun getField(): Field? = findField()

        /**
         * 查找所有匹配的字段（可能来自当前类及其父类，取决于 recursive）。
         *
         * @param includeParents 是否包含父类层级（等价于临时启用 recursive 的效果，但不改变 Finder 的状态）
         * @param predicate 可选二次过滤：例如按字段当前值是否为 null、是否包含某个对象等
         */
        fun findAll(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): List<Field> {
            require(fieldName != null || fieldType != null) {
                "At least one search condition (name or type) must be specified"
            }

            val results = ArrayList<Field>()
            val visited = HashSet<String>(32)

            fun addField(f: Field) {
                val key = "${f.declaringClass.name}#${f.name}:${f.type.name}"
                if (!visited.add(key)) return

                makeAccessible(f)
                if (predicate == null || predicate.invoke(f)) {
                    results.add(f)
                }
            }

            fun scanClass(clazz: Class<*>) {
                when {
                    fieldName != null -> {
                        val f = findByName(clazz, fieldName!!, fieldType)
                        if (f != null) addField(f)
                    }
                    else -> {
                        val type = fieldType ?: return
                        val instanceFields = ArrayList<Field>()
                        val staticFields = ArrayList<Field>()

                        for (f in clazz.declaredFields) {
                            if (!isTypeMatch(type, f.type)) continue
                            if (Modifier.isStatic(f.modifiers)) staticFields.add(f) else instanceFields.add(f)
                        }

                        val ordered = if (preferInstance) {
                            instanceFields + staticFields
                        } else {
                            staticFields + instanceFields
                        }

                        ordered.forEach { addField(it) }
                    }
                }
            }

            parentClass?.let { p ->
                val tc = target.targetClass()
                if (p.isAssignableFrom(tc)) {
                    scanClass(p)
                    if (includeParents) {
                        var c: Class<*>? = p.superclass
                        while (c != null && c != Any::class.java) {
                            scanClass(c)
                            c = c.superclass
                        }
                    }
                }
            }

            var c: Class<*>? = target.targetClass()
            while (c != null && c != Any::class.java) {
                scanClass(c)
                if (!includeParents) break
                c = c.superclass
            }

            return results
        }

        /** 返回所有字段的当前值（会对每个 Field 做一次 get） */
        fun findAllValues(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): List<Any?> = findAll(includeParents, predicate).map { get(it) }

        fun findFirst(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): Field? = findAll(includeParents, predicate).firstOrNull()

        // -------- 内部实现 --------

        private fun findField(): Field? {
            require(fieldName != null || fieldType != null) {
                "At least one search condition (name or type) must be specified"
            }

            // 先从 parentClass 找
            parentClass?.let { p ->
                val tc = target.targetClass()
                if (p.isAssignableFrom(tc)) {
                    findInClass(p)?.let { return it }
                    if (recursive) {
                        // 从 p 往上爬
                        var c: Class<*>? = p.superclass
                        while (c != null && c != Any::class.java) {
                            findInClass(c)?.let { return it }
                            c = c.superclass
                        }
                    }
                }
            }

            // 再从 targetClass 开始找
            var clazz: Class<*>? = target.targetClass()
            while (clazz != null && clazz != Any::class.java) {
                findInClass(clazz)?.let { return it }
                if (!recursive) break
                clazz = clazz.superclass
            }
            return null
        }

        private fun findInClass(clazz: Class<*>): Field? {
            // 尝试缓存命中
            fieldCacheKey(clazz)?.let { key ->
                FieldCache[key]?.let { return it }
            }

            val result = when {
                fieldName != null -> findByName(clazz, fieldName!!, fieldType)
                else -> findByType(clazz, fieldType!!)
            }

            // 写入缓存
            if (result != null) {
                makeAccessible(result)
                fieldCacheKey(clazz)?.let { key -> FieldCache[key] = result }
            }
            return result
        }

        private fun findByName(clazz: Class<*>, name: String, type: Class<*>?): Field? {
            return runCatching {
                val f = clazz.getDeclaredField(name)
                if (type == null) return@runCatching f

                if (isTypeMatch(type, f.type)) f else null
            }.getOrNull()
        }

        private fun findByType(clazz: Class<*>, type: Class<*>): Field? {
            val instanceFields = ArrayList<Field>()
            val staticFields = ArrayList<Field>()

            for (f in clazz.declaredFields) {
                if (!isTypeMatch(type, f.type)) continue
                if (Modifier.isStatic(f.modifiers)) staticFields.add(f) else instanceFields.add(f)
            }

            val list = if (preferInstance) {
                instanceFields.ifEmpty { staticFields }
            } else {
                staticFields.ifEmpty { instanceFields }
            }

            return list.getOrNull(index)
        }

        private fun isTypeMatch(expected: Class<*>, actual: Class<*>): Boolean {
            // expected 能接 actual
            if (expected.isAssignableFrom(actual)) return true

            // classloader 不同导致 assignable 失效时，按名字判断
            if (matchTypeByNameFallback && expected.name == actual.name) return true

            return false
        }

        private fun makeAccessible(field: Field) {
            if (!Modifier.isPublic(field.modifiers) || !Modifier.isPublic(field.declaringClass.modifiers)) {
                field.isAccessible = true
            }
        }

        private fun get(field: Field): Any? {
            makeAccessible(field)
            return if (Modifier.isStatic(field.modifiers)) field.get(null) else field.get(target.instanceOrNull())
        }

        private fun set(field: Field, value: Any?) {
            makeAccessible(field)
            if (Modifier.isStatic(field.modifiers)) {
                field.set(null, value)
            } else {
                field.set(target.instanceOrNull(), value)
            }
        }

        private fun fieldCacheKey(clazz: Class<*>): String? {
            val keyName = fieldName
            val keyType = fieldType?.name
            if (keyName == null && keyType == null) return null
            return buildString {
                append(clazz.name).append('#')
                append(keyName ?: "<no_name>").append('|')
                append(keyType ?: "<no_type>").append('|')
                append("idx=").append(index).append('|')
                append("pi=").append(preferInstance).append('|')
                append("fb=").append(matchTypeByNameFallback)
            }
        }
    }

    internal sealed class Target {
        abstract fun targetClass(): Class<*>
        abstract fun instanceOrNull(): Any?

        data class Instance(val obj: Any) : Target() {
            override fun targetClass(): Class<*> = obj.javaClass
            override fun instanceOrNull(): Any = obj
        }

        data class StaticClass(val clazz: Class<*>) : Target() {
            override fun targetClass(): Class<*> = clazz
            override fun instanceOrNull(): Any? = null
        }
    }

    private val FieldCache = ConcurrentHashMap<String, Field>()

    fun clearCache() = FieldCache.clear()
}
