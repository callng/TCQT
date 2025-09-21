package com.owo233.tcqt.ext

import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.isStatic
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Constructor
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

internal fun beforeHook(
    ver: Int = XCallback.PRIORITY_DEFAULT,
    block: (param: XC_MethodHook.MethodHookParam) -> Unit
): XC_MethodHook {
    return object :XC_MethodHook(ver) {
        override fun beforeHookedMethod(param: MethodHookParam) {
            kotlin.runCatching {
                block(param)
            }.onFailure {
                Log.e("beforeHook 异常", it)
            }
        }
    }
}

internal fun afterHook(
    ver: Int = XCallback.PRIORITY_DEFAULT,
    block: (param: XC_MethodHook.MethodHookParam) -> Unit
): XC_MethodHook {
    return object :XC_MethodHook(ver) {
        override fun afterHookedMethod(param: MethodHookParam) {
            kotlin.runCatching {
                block(param)
            }.onFailure {
                Log.e("afterHook 异常", it)
            }
        }
    }
}

internal fun replaceHook(
    ver: Int = XCallback.PRIORITY_DEFAULT,
    block: (param: XC_MethodHook.MethodHookParam) -> Any?
): XC_MethodHook {
    return object : XC_MethodReplacement(ver) {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            return kotlin.runCatching {
                block(param)
            }.onFailure {
                Log.e("replaceHook 异常", it)
            }.getOrNull()
        }
    }
}

internal fun XC_MethodHook.MethodHookParam.invokeOriginal(vararg newArgs: Any?): Any? {
    val isStatic = method.isStatic
    val receiver = if (isStatic) null else thisObject
    val argsToUse = if (newArgs.isNotEmpty()) newArgs else args
    return XposedBridge.invokeOriginalMethod(method, receiver, argsToUse)
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

typealias MethodCondition = Method.() -> Boolean

internal fun findMethod(
    clz: Class<*>,
    findSuper: Boolean = false,
    condition: MethodCondition
): Method {
    return findMethodOrNull(clz, findSuper, condition) ?: throw NoSuchMethodException()
}

internal fun findMethodOrNull(
    clz: Class<*>,
    findSuper: Boolean = false,
    condition: MethodCondition
): Method? {
    var c = clz
    c.declaredMethods.firstOrNull { it.condition() }
        ?.let { it.isAccessible = true;return it }

    if (findSuper) {
        while (c.superclass?.also { c = it } != null) {
            c.declaredMethods.firstOrNull { it.condition() }
                ?.let { it.isAccessible = true;return it }
        }
    }
    return null
}

object FuzzyClassKit {
    private val dic = arrayOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
        "u", "v", "w", "x", "y", "z"
    )

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
            clz?.declaredMethods?.forEach {
                if (check(clz, it)) return it
            }
        }

        return null
    }

    fun findMethodByClassName(prefix: String, check: (Method) -> Boolean): Method? {
        dic.forEach { name->
            val clz = XpClassLoader.load("$prefix.$name")
            clz?.declaredMethods?.forEach {
                if (check(it)) return it
            }
        }

        return null
    }

    fun findClassByMethod(prefix: String, isSubClass: Boolean = false, check: (Class<*>, Method) -> Boolean): Class<*>? {
        dic.forEach { name ->
            val clz = XpClassLoader.load("$prefix${if (isSubClass) "$" else "."}$name")
            clz?.declaredMethods?.forEach {
                if (check(clz, it)) return clz
            }
        }

        return  null
    }

    fun findClassByConstructor(
        prefix: String,
        isSubClass: Boolean = false,
        check: (Class<*>, Constructor<*>) -> Boolean
    ): Class<*>? {
        dic.forEach { name ->
            val clz = XpClassLoader.load("$prefix${if (isSubClass) "$" else "."}$name")
            clz?.declaredConstructors?.forEach {
                if (check(clz, it)) return clz
            }
        }

        return null
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

    private fun getAllMethods(clazz: Class<*>): List<Method> {
        val methods = mutableListOf<Method>()
        var currentClass: Class<*>? = clazz

        while(currentClass != null && currentClass != Any::class.java) {
            methods.addAll(currentClass.declaredMethods)
            currentClass = currentClass.superclass
        }

        return methods
    }
}
