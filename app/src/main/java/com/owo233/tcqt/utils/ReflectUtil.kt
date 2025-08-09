@file:JvmName("ReflectUtil")
package com.owo233.tcqt.utils

import cn.hutool.core.util.ClassUtil
import cn.hutool.core.util.ClassUtil.isAllAssignableFrom
import cn.hutool.core.util.ReflectUtil
import java.lang.reflect.Field
import java.lang.reflect.Method

fun Any.getMethods(withSuper: Boolean = true): Array<Method> {
    val clazz = this as? Class<*> ?: this::class.java
    return ReflectUtil.getMethodsDirectly(clazz, withSuper, false)
}

fun Any.getFields(withSuper: Boolean = true): Array<Field> {
    val clazz = this as? Class<*> ?: this::class.java
    return ReflectUtil.getFieldsDirectly(clazz, withSuper)
}

fun Any.field(
    fieldType: Class<*>,
    withSuper: Boolean = true
): Field? {
    this.getFields(withSuper).forEach {
        if (it.type == fieldType) {
            it.isAccessible = true
            return it
        }
    }
    return null
}

fun Any.field(
    fieldName: String,
    withSuper: Boolean = true
): Field? {
    this.getFields(withSuper).forEach {
        if (it.name == fieldName) {
            it.isAccessible = true
            return it
        }
    }
    return null
}

fun Any.fieldValue(
    fieldType: Class<*>,
    withSuper: Boolean = true
): Any? {
    this.getFields(withSuper).forEach {
        if (it.type == fieldType) {
            return ReflectUtil.getFieldValue(this, it)
        }
    }
    return null
}

fun Any.fieldValue(
    name: String,
    withSuper: Boolean = true
): Any? {
    this.getFields(withSuper).forEach {
        if (it.name == name) {
            return ReflectUtil.getFieldValue(this, it)
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(
    fieldType: Class<*>,
    withSuper: Boolean = true
): T? = this.fieldValue(fieldType, withSuper) as T?

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(
    name: String,
    withSuper: Boolean = true
): T? = this.fieldValue(name, withSuper) as T?

fun <T> Any.invoke(
    name: String,
    returnType: Class<T>,
    vararg args: Any?,
    withSuper: Boolean = true
): T? {
    this.getMethods(withSuper).let { arrayOfMethods ->
        arrayOfMethods.forEach {
            if (it.name == name && it.returnType == returnType
                && isAllAssignableFrom(ClassUtil.getClasses(*args), it.parameterTypes)
            ) {
                return ReflectUtil.invoke(this, it, *args)
            }
        }
        return null
    }
}

fun Any.invoke(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): Any? {
    this.getMethods(withSuper).let { arrayOfMethods ->
        arrayOfMethods.forEach {
            if (it.name == name
                && isAllAssignableFrom(it.parameterTypes, ClassUtil.getClasses(*args))
            ) {
                return ReflectUtil.invoke(this, it, *args)
            }
        }
        return null
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.invokeAs(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): T? = this.invoke(name, *args, withSuper = withSuper) as T?

fun <T> Class<T>.new(
    vararg args: Any?
): T = ReflectUtil.newInstance(this, *args)

fun Any.setValue(name: String, value: Any) {
    this.field(name)?.set(this, value)
}
