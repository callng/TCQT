package com.owo233.tcqt.utils.reflect

import com.owo233.tcqt.utils.log.Log
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object FieldUtils {

    /**
     * 创建一个字段查找器实例。
     *
     * @param target 目标对象。可以是实例对象 (Instance) 或 类对象 (Class)。
     * 如果是 Class，通常用于查找静态字段。
     */
    fun create(target: Any?): Finder = Finder(target)

    class Finder(private val targetInstance: Any?) {

        private var fieldName: String? = null
        private var fieldType: Class<*>? = null
        private var searchClass: Class<*>? = null
        private var recursive: Boolean = false
        private var fieldIndex: Int = 0

        private var cachedField: Field? = null
        private var resolved: Boolean = false

        /**
         * **指定字段名称**
         *
         * @param name 字段名 (e.g. "mContext")
         */
        fun named(name: String) = apply { fieldName = name }

        /**
         * **指定字段类型 (Java Class)**
         *
         * @param type 字段的类型 class (e.g. String::class.java)
         */
        fun typed(type: Class<*>) = apply { fieldType = type }

        /**
         * **指定字段类型 (Kotlin Reified)**
         *
         * 用法: `.typed<String>()`
         */
        inline fun <reified T> typed() = typed(T::class.java)

        /**
         * **强制指定搜索的类**
         *
         * 默认会从 `targetInstance.javaClass` 开始找。
         * 如果需要直接跳过子类，直接去某个父类找，可以用这个。
         */
        fun inClass(clazz: Class<*>) = apply { searchClass = clazz }

        /**
         * **递归查找父类**
         *
         * @param enable 是否在当前类找不到时，向上递归父类查找 (默认 true 开启)
         */
        fun recursive(enable: Boolean = true) = apply { recursive = enable }

        /**
         * **指定索引**
         *
         * 当按类型查找 (`typed`) 匹配到多个字段时，取第几个？
         * @param index 索引，从 0 开始
         */
        fun atIndex(index: Int) = apply { fieldIndex = index }

        /**
         * **获取值**
         *
         * 自动处理 `field.get()` 并强转为泛型 T。
         * 内部使用了 `runCatching`，发生异常(如类型不匹配)会静默返回 null。
         *
         * @return 字段的值，失败返回 null
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> get(): T? {
            val field = resolveField() ?: return null
            return runCatching {
                if (Modifier.isStatic(field.modifiers)) {
                    field.get(null)
                } else {
                    field.get(targetInstance)
                }
            }.getOrNull() as? T
        }

        /**
         * **设置值**
         *
         * 将值注入到字段中。如果失败会打印 Error 日志。
         *
         * @param value 要设置的新值
         */
        fun set(value: Any?) {
            val field = resolveField() ?: return
            runCatching {
                if (Modifier.isStatic(field.modifiers)) {
                    field.set(null, value)
                } else {
                    field.set(targetInstance, value)
                }
            }.onFailure {
                Log.e("FieldUtils: set failed", it)
            }
        }

        /**
         * **获取原始 Field 对象**
         *
         * 如果需要做更底层的操作（比如查看注解），可以直接拿 Field。
         */
        fun getField(): Field? = resolveField()

        private fun resolveField(): Field? {
            if (resolved) return cachedField
            resolved = true

            val startClass = searchClass
                ?: (targetInstance as? Class<*>)
                ?: targetInstance?.javaClass
                ?: return null

            val fields = mutableListOf<Field>()
            var cls: Class<*>? = startClass

            while (cls != null && cls != Any::class.java) {
                fields += cls.declaredFields
                cls = if (recursive) cls.superclass else null
            }

            val matched = fields.filter { f ->
                val nameMatch = fieldName == null || f.name == fieldName
                val typeMatch = fieldType == null || fieldType!!.isAssignableFrom(f.type)
                nameMatch && typeMatch
            }

            cachedField = matched.getOrNull(fieldIndex)?.apply {
                isAccessible = true
            }

            return cachedField
        }
    }
}
