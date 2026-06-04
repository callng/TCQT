package com.owo233.tcqt.hooks.func.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import com.owo233.tcqt.utils.reflect.FieldUtils

internal fun <T> Any.fieldValueByType(type: Class<T>): T? {
    val value = FieldUtils.create(this)
        .typed(type)
        .recursive(true)
        .getOrNull()

    return if (type.isInstance(value)) type.cast(value) else null
}

internal fun Any.stringField(name: String): String? {
    return runCatching {
        FieldUtils.create(this).named(name).recursive(true).getOrNull() as? String
    }.getOrNull()
}

internal fun Any.intField(name: String): Int? {
    return runCatching {
        (FieldUtils.create(this).named(name).recursive(true).getOrNull() as? Number)?.toInt()
    }.getOrNull()
}

internal fun Any.longField(name: String): Long? {
    return runCatching {
        (FieldUtils.create(this).named(name).recursive(true).getOrNull() as? Number)?.toLong()
    }.getOrNull()
}

internal fun Any.byteField(name: String): Byte? {
    return runCatching {
        (FieldUtils.create(this).named(name).recursive(true).getOrNull() as? Number)?.toByte()
    }.getOrNull()
}

internal fun Any.anyField(name: String): Any? {
    return runCatching {
        FieldUtils.create(this).named(name).recursive(true).getOrNull()
    }.getOrNull()
}

internal fun Any.mutableMapField(name: String): MutableMap<*, *>? {
    return runCatching {
        FieldUtils.create(this).named(name).recursive(true).getOrNull() as? MutableMap<*, *>
    }.getOrNull()
}

internal fun Any.setFieldValue(name: String, value: Any?) {
    runCatching {
        FieldUtils.create(this)
            .named(name)
            .recursive(true)
            .setValue(value)
    }
}
