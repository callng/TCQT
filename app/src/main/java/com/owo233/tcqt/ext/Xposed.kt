package com.owo233.tcqt.ext

import com.owo233.tcqt.utils.logE
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal typealias MethodHooker = (XC_MethodHook.MethodHookParam) -> Unit

internal class XCHook {
    var before = nullableOf<MethodHooker>()
    var after = nullableOf<MethodHooker>()

    fun after(after: MethodHooker): XCHook {
        this.after.set(after)
        return this
    }

    fun before(before: MethodHooker): XCHook {
        this.before.set(before)
        return this
    }
}

internal fun Class<*>.hookMethod(name: String): XCHook {
    return XCHook().also {
        XposedBridge.hookAllMethods(this, name, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                it.before.getOrNull()?.invoke(param)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                it.after.getOrNull()?.invoke(param)
            }
        })
    }
}

internal fun Class<*>.hookMethod(name: String, hook: XC_MethodHook) {
    XposedBridge.hookAllMethods(this, name, hook)
}

internal fun Method.hookMethod(hook: XC_MethodHook) {
    XposedBridge.hookMethod(this, hook)
}

internal fun beforeHook(ver: Int = XCallback.PRIORITY_DEFAULT, block: (param: XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook {
    return object :XC_MethodHook(ver) {
        override fun beforeHookedMethod(param: MethodHookParam) {
            kotlin.runCatching {
                block(param)
            }.onFailure {
                logE(msg = "beforeHook 异常", cause = it)
            }
        }
    }
}

internal fun afterHook(ver: Int = XCallback.PRIORITY_DEFAULT, block: (param: XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook {
    return object :XC_MethodHook(ver) {
        override fun afterHookedMethod(param: MethodHookParam) {
            kotlin.runCatching {
                block(param)
            }.onFailure {
                logE(msg = "afterHook 异常", cause = it)
            }
        }
    }
}

internal fun Any.field(
    fieldName: String,
    isStatic: Boolean = false,
    fieldType: Class<*>? = null
): Field {
    if (fieldName.isBlank()) throw IllegalArgumentException("Field name must not be empty!")
    var c: Class<*> = this as? Class<*> ?: this.javaClass
    do {
        c.declaredFields
            .filter { isStatic == Modifier.isStatic(it.modifiers) }
            .firstOrNull { (fieldType == null || it.type == fieldType) && (it.name == fieldName) }
            ?.let { it.isAccessible = true;return it }
    } while (c.superclass?.also { c = it } != null)
    throw NoSuchFieldException("Name: $fieldName,Static: $isStatic, Type: ${if (fieldType == null) "ignore" else fieldType.name}")
}

internal fun Class<*>.staticField(fieldName: String, type: Class<*>? = null): Field {
    if (fieldName.isBlank()) throw IllegalArgumentException("Field name must not be empty!")
    return this.field(fieldName, true, type)
}

internal fun Class<*>.getStaticObject(
    objName: String,
    type: Class<*>? = null
): Any {
    if (objName.isBlank()) throw IllegalArgumentException("Object name must not be empty!")
    return this.staticField(objName, type).get(this)!!
}

object FuzzyClassKit {
    private val dic = arrayOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
        "u", "v", "w", "x", "y", "z"
    )

    fun findClassByField(prefix: String, check: (Field) -> Boolean): Class<*>? {
        dic.forEach { className ->
            val clz = XpClassLoader.load("$prefix.$className")
            clz?.fields?.forEach {
                if (it.modifiers and Modifier.STATIC != 0
                    && !isBaseType(it.type)
                    && check(it)
                ) return clz
            }
        }

        return null
    }

    fun findClassesByField(
        classLoader: ClassLoader = FuzzyClassKit::class.java.classLoader ?: XpClassLoader,
        prefix: String,
        check: (Class<*>, Field) -> Boolean
    ): List<Class<*>> {
        val list = arrayListOf<Class<*>>()
        dic.forEach { className ->
            kotlin.runCatching {
                val clz = classLoader.loadClass("$prefix.$className")
                clz.declaredFields.forEach {
                    if (!isBaseType(it.type) && check(clz, it)) {
                        list.add(clz)
                    }
                }
            }
        }

        return list
    }

    fun findMethodByClassPrefix(prefix: String, isSubClass: Boolean = false, check: (Class<*>, Method) -> Boolean): Method? {
        dic.forEach { className ->
            val clz = XpClassLoader.load("$prefix${if (isSubClass) "$" else "."}$className")
            clz?.methods?.forEach {
                if (check(clz, it)) return it
            }
        }

        return null
    }

    fun findClassesByMethod(prefix: String, isSubClass: Boolean = false, check: (Class<*>, Method) -> Boolean): List<Class<*>> {
        val arrayList = arrayListOf<Class<*>>()
        dic.forEach { className ->
            val clz = XpClassLoader.load("$prefix${if (isSubClass) "$" else "."}$className")
            clz?.methods?.forEach {
                if (check(clz, it)) arrayList.add(clz)
            }
        }

        return arrayList
    }

    private fun isBaseType(clz: Class<*>): Boolean {
        return clz == Long::class.java ||
                clz == Double::class.java ||
                clz == Float::class.java ||
                clz == Int::class.java ||
                clz == Short::class.java ||
                clz == Char::class.java ||
                clz == Byte::class.java
    }
}
