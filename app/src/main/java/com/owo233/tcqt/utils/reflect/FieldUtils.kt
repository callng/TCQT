package com.owo233.tcqt.utils.reflect

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 兼容旧链式调用方式的字段工具。
 *
 * 实际搜索统一委托给 [FieldSearcher]，不再维护第二套缓存和类型匹配规则。
 */
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
        private var index: Int = 0
        private var preferInstance: Boolean = true
        private var matchTypeByNameFallback: Boolean = false
        private var includeSynthetic: Boolean = true
        private var requireUnique: Boolean = false

        fun named(name: String) = apply { fieldName = name }

        fun typed(type: Class<*>) = typedInternal(type)

        @PublishedApi
        internal fun typedInternal(type: Class<*>) = apply { fieldType = type }

        inline fun <reified T> typed() = typedInternal(T::class.java)

        fun inParent(parent: Class<*>) = apply { parentClass = parent }

        fun recursive(enable: Boolean = true) = apply { recursive = enable }

        fun index(i: Int) = apply { index = i.coerceAtLeast(0) }

        fun preferInstance(enable: Boolean = true) = apply { preferInstance = enable }

        fun sameNameTypeMatch(enable: Boolean = true) = apply {
            matchTypeByNameFallback = enable
        }

        fun includeSynthetic(enable: Boolean = true) = apply {
            includeSynthetic = enable
        }

        fun requireUnique(enable: Boolean = true) = apply {
            requireUnique = enable
        }

        fun getValue(): Any? = findField()?.let(::get)

        fun getOrNull(): Any? = runCatching { getValue() }.getOrNull()

        /** 字段存在时允许返回 null，字段不存在时抛出异常。 */
        fun getOrThrow(): Any? {
            val field = findField() ?: throw NoSuchFieldException(errorMessage())
            return get(field)
        }

        fun setValue(value: Any?) {
            findField()?.let { set(it, value) }
        }

        fun setOrThrow(value: Any?) {
            val field = findField() ?: throw NoSuchFieldException(errorMessage())
            set(field, value)
        }

        fun getField(): Field? = findField()

        fun findAll(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): List<Field> {
            validateCriteria()
            val fields = target.targetClass().findFields {
                applyToSearcher(this, includeParents, applyIndex = false)
            }
            return if (predicate == null) fields else fields.filter(predicate)
        }

        fun findAllValues(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): List<Any?> = findAll(includeParents, predicate).map(::get)

        fun findFirst(
            includeParents: Boolean = recursive,
            predicate: ((Field) -> Boolean)? = null
        ): Field? = findAll(includeParents, predicate).firstOrNull()

        private fun findField(): Field? {
            validateCriteria()
            return target.targetClass().findFieldOrNull {
                applyToSearcher(this, recursive, applyIndex = true)
            }
        }

        private fun applyToSearcher(
            searcher: FieldSearcher,
            includeParents: Boolean,
            applyIndex: Boolean
        ) {
            searcher.name = fieldName
            searcher.type = fieldType
            searcher.inParent = parentClass
            searcher.scope = if (includeParents) SearchScope.SUPERCLASSES else SearchScope.DECLARED
            searcher.preferInstance = preferInstance
            searcher.sameNameTypeMatch = matchTypeByNameFallback
            searcher.includeSynthetic = includeSynthetic
            searcher.requireUnique = requireUnique
            searcher.index = if (applyIndex) index else 0

            when (target) {
                is Target.Instance -> Unit
                is Target.StaticClass -> searcher.isStatic = true
            }
        }

        private fun validateCriteria() {
            require(fieldName != null || fieldType != null) {
                "At least one search condition (name or type) must be specified"
            }
        }

        private fun get(field: Field): Any? {
            field.makeAccessible()
            return if (Modifier.isStatic(field.modifiers)) {
                field.get(null)
            } else {
                val instance = target.instanceOrNull()
                    ?: throw IllegalStateException(
                        "Cannot read instance field ${field.declaringClass.name}#${field.name} from a static target"
                    )
                field.get(instance)
            }
        }

        private fun set(field: Field, value: Any?) {
            field.makeAccessible()
            if (Modifier.isStatic(field.modifiers)) {
                field.set(null, value)
            } else {
                val instance = target.instanceOrNull()
                    ?: throw IllegalStateException(
                        "Cannot write instance field ${field.declaringClass.name}#${field.name} from a static target"
                    )
                field.set(instance, value)
            }
        }

        private fun errorMessage(): String {
            return "Field not found. name=$fieldName type=${fieldType?.name} " +
                "target=${target.targetClass().name} parent=${parentClass?.name} recursive=$recursive"
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

    fun clearCache() = ReflectCache.clearFields()
}
