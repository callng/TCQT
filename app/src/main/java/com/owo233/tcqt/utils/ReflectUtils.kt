@file:JvmName("ReflectUtil")
package com.owo233.tcqt.utils

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
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
