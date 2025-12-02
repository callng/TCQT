@file:JvmName("ReflectUtil")
package com.owo233.tcqt.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

private val fieldCache = ConcurrentHashMap<Pair<Class<*>, Boolean>, Array<Field>>()
private val methodCache = ConcurrentHashMap<Pair<Class<*>, Boolean>, Array<Method>>()
private val constructorCache = ConcurrentHashMap<Class<*>, Array<Constructor<*>>>()

private fun Class<*>.allFields(withSuper: Boolean): Array<Field> {
    return fieldCache.getOrPut(this to withSuper) {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = this
        while (current != null) {
            fields += current.declaredFields
            current = if (withSuper) current.superclass else null
        }
        fields.toTypedArray()
    }
}

private fun Class<*>.allMethods(withSuper: Boolean): Array<Method> {
    return methodCache.getOrPut(this to withSuper) {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = this
        while (current != null) {
            methods += current.declaredMethods
            current = if (withSuper) current.superclass else null
        }
        methods.toTypedArray()
    }
}

private fun Class<*>.allConstructors(): Array<Constructor<*>> {
    return constructorCache.getOrPut(this) {
        this.declaredConstructors.clone()
    }
}

fun Any.getMethods(withSuper: Boolean = true): Array<Method> {
    val clazz = (this as? Class<*>) ?: this::class.java
    return clazz.allMethods(withSuper)
}

fun Any.getFields(withSuper: Boolean = true): Array<Field> {
    val clazz = (this as? Class<*>) ?: this::class.java
    return clazz.allFields(withSuper)
}

fun Any.field(fieldType: Class<*>, withSuper: Boolean = true): Field? {
    return this.getFields(withSuper).firstOrNull { it.type == fieldType }
        ?.apply { isAccessible = true }
}

fun Any.field(fieldName: String, withSuper: Boolean = true): Field? {
    return this.getFields(withSuper).firstOrNull { it.name == fieldName }
        ?.apply { isAccessible = true }
}

fun Any.fieldValue(fieldType: Class<*>, withSuper: Boolean = true): Any? {
    return this.field(fieldType, withSuper)?.get(this)
}

fun Any.fieldValue(name: String, withSuper: Boolean = true): Any? {
    return this.field(name, withSuper)?.get(this)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(fieldType: Class<*>, withSuper: Boolean = true): T? =
    this.fieldValue(fieldType, withSuper) as T?

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(name: String, withSuper: Boolean = true): T? =
    this.fieldValue(name, withSuper) as T?

fun <T> Any.invoke(
    name: String,
    returnType: Class<T>,
    vararg args: Any?,
    withSuper: Boolean = true
): T? {
    val clazz = (this as? Class<*>) ?: this::class.java
    clazz.allMethods(withSuper).forEach {
        if (it.name == name
            && it.returnType == returnType
            && parametersMatch(it.parameterTypes, args)
        ) {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return it.invoke(this, *args) as T?
        }
    }
    return null
}

fun Any.invoke(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): Any? {
    val clazz = (this as? Class<*>) ?: this::class.java
    clazz.allMethods(withSuper).forEach {
        if (it.name == name && parametersMatch(it.parameterTypes, args)) {
            it.isAccessible = true
            return try {
                it.invoke(this, *args)
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.invokeAs(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): T? = this.invoke(name, *args, withSuper = withSuper) as T?

@Suppress("UNCHECKED_CAST")
fun <T> Class<T>.new(vararg args: Any?): T {
    this.allConstructors().forEach { c ->
        if (parametersMatch(c.parameterTypes, args)) {
            c.isAccessible = true
            return c.newInstance(*args) as T
        }
    }
    throw NoSuchMethodException("No matching constructor found for ${this.name}")
}

fun Any.setValue(name: String, value: Any): Boolean {
    val field = this.field(name) ?: return false
    field.set(this, value)
    return true
}

private fun parametersMatch(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
    if (paramTypes.size != args.size) return false
    for (i in paramTypes.indices) {
        val arg = args[i]
        val param = paramTypes[i]
        if (arg == null) {
            if (param.isPrimitive) return false
        } else {
            val argClass = arg.javaClass
            if (!isAssignable(param, argClass)) return false
        }
    }
    return true
}

private fun wrap(clazz: Class<*>): Class<*> = when (clazz) {
    java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
    java.lang.Byte.TYPE -> java.lang.Byte::class.java
    Character.TYPE -> Character::class.java
    java.lang.Short.TYPE -> java.lang.Short::class.java
    Integer.TYPE -> Integer::class.java
    java.lang.Long.TYPE -> java.lang.Long::class.java
    java.lang.Float.TYPE -> java.lang.Float::class.java
    java.lang.Double.TYPE -> java.lang.Double::class.java
    else -> clazz
}

private fun isAssignable(param: Class<*>, argClass: Class<*>): Boolean {
    return when {
        param == argClass -> true
        wrap(param).isAssignableFrom(wrap(argClass)) -> true
        isWideningPrimitive(param, argClass) -> true
        else -> false
    }
}

private fun isWideningPrimitive(param: Class<*>, argClass: Class<*>): Boolean {
    return when (argClass) {
        java.lang.Byte.TYPE -> param in arrayOf(
            java.lang.Short.TYPE, Integer.TYPE, java.lang.Long.TYPE,
            java.lang.Float.TYPE, java.lang.Double.TYPE
        )
        java.lang.Short.TYPE, Character.TYPE -> param in arrayOf(
            Integer.TYPE, java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Double.TYPE
        )
        Integer.TYPE -> param in arrayOf(java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Double.TYPE)
        java.lang.Long.TYPE -> param in arrayOf(java.lang.Float.TYPE, java.lang.Double.TYPE)
        java.lang.Float.TYPE -> param == java.lang.Double.TYPE
        else -> false
    }
}

/**
 * 将任意对象转换为JSON格式字符串，通过反射获取所有字段
 * @param maxDepth 最大递归深度，防止循环引用导致栈溢出，默认3层
 * @param withSuper 是否包含父类字段，默认true
 * @return JSON格式字符串
 */
fun Any?.toJsonString(maxDepth: Int = 3, withSuper: Boolean = true): String {
    return this.toJsonObject(maxDepth, withSuper, mutableSetOf()).toString()
}

/**
 * 将任意对象转换为JsonObject，通过反射获取所有字段
 * @param maxDepth 最大递归深度
 * @param withSuper 是否包含父类字段
 * @param visited 已访问对象集合，用于防止循环引用
 */
private fun Any?.toJsonObject(
    maxDepth: Int,
    withSuper: Boolean,
    visited: MutableSet<Int>
): JsonObject = buildJsonObject {
    if (this@toJsonObject == null) return@buildJsonObject

    // 防止循环引用
    val objectId = System.identityHashCode(this@toJsonObject)
    if (objectId in visited || maxDepth <= 0) {
        put("_ref", JsonPrimitive("@${this@toJsonObject.javaClass.simpleName}#${Integer.toHexString(objectId)}"))
        return@buildJsonObject
    }
    visited.add(objectId)

    try {
        // 添加类型信息
        put("_class", JsonPrimitive(this@toJsonObject.javaClass.name))

        // 获取所有字段
        val fields = this@toJsonObject.getFields(withSuper)

        fields.forEach { field ->
            try {
                // 跳过静态字段
                if (Modifier.isStatic(field.modifiers)) return@forEach

                // 跳过ART虚拟机的影子字段（shadow fields）
                val fieldName = field.name
                if (fieldName.startsWith("shadow$")) return@forEach

                field.isAccessible = true
                when (val value = field.get(this@toJsonObject)) {
                    null -> put(fieldName, JsonPrimitive(null as String?))
                    is String -> put(fieldName, JsonPrimitive(value))
                    is Number -> put(fieldName, JsonPrimitive(value))
                    is Boolean -> put(fieldName, JsonPrimitive(value))
                    is Char -> put(fieldName, JsonPrimitive(value.toString()))
                    is Enum<*> -> put(fieldName, JsonPrimitive(value.name))
                    is CharSequence -> put(fieldName, JsonPrimitive(value.toString()))
                    is Array<*> -> {
                        // 数组转为字符串表示
                        put(fieldName, JsonPrimitive(value.contentToString()))
                    }
                    is Collection<*> -> {
                        // 集合转为字符串表示
                        put(fieldName, JsonPrimitive(value.toString()))
                    }
                    is Map<*, *> -> {
                        // Map转为字符串表示
                        put(fieldName, JsonPrimitive(value.toString()))
                    }
                    is WeakReference<*> -> {
                        // 处理弱引用
                        val target = value.get()
                        if (target == null) {
                            put(fieldName, JsonPrimitive("<cleared>"))
                        } else {
                            put(fieldName, target.toJsonObject(maxDepth - 1, withSuper, visited))
                        }
                    }
                    else -> {
                        // 递归处理对象类型
                        if (maxDepth > 1 && !value.javaClass.name.startsWith("java.")
                            && !value.javaClass.name.startsWith("android.")) {
                            put(fieldName, value.toJsonObject(maxDepth - 1, withSuper, visited))
                        } else {
                            // 其他类型转为字符串
                            put(fieldName, JsonPrimitive(value.toString()))
                        }
                    }
                }
            } catch (e: Exception) {
                put(field.name, JsonPrimitive("<error: ${e.message}>"))
            }
        }
    } catch (e: Exception) {
        put("_error", JsonPrimitive(e.message ?: "Unknown error"))
    } finally {
        visited.remove(objectId)
    }
}
