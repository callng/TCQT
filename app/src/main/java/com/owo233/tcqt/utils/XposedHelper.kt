@file:Suppress("unused")

package com.owo233.tcqt.utils

import android.content.res.XResources
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.log.Log
import dalvik.system.BaseDexClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedBridge.invokeOriginalMethod
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findField
import de.robv.android.xposed.XposedHelpers.findFieldIfExists
import de.robv.android.xposed.XposedHelpers.findFirstFieldByExactType
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getLongField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setFloatField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setLongField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.XposedHelpers.setStaticObjectField
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Enumeration

typealias MethodHookParam = MethodHookParam
typealias Replacer = (MethodHookParam) -> Any?
typealias Hooker = (MethodHookParam) -> Unit

fun Class<*>.hookMethod(method: String?, vararg args: Any?): XC_MethodHook.Unhook = try {
    findAndHookMethod(this, method, *args)
} catch (e: Throwable) {
    throw e
}

fun Member.hookMethod(callback: XC_MethodHook): XC_MethodHook.Unhook = try {
    hookMethod(this, callback)
} catch (e: Throwable) {
    throw e
}

inline fun MethodHookParam.callHooker(crossinline hooker: Hooker) = try {
    hooker(this)
} catch (e: Throwable) {
    Log.e("Error occurred calling hooker on ${this.method}", e)
}

inline fun MethodHookParam.callReplacer(crossinline replacer: Replacer) = try {
    replacer(this)
} catch (e: Throwable) {
    Log.e("Error occurred calling replacer on ${this.method}", e)
}

inline fun Member.replaceMethod(crossinline replacer: Replacer) =
    hookMethod(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
    })

inline fun beforeHook(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    crossinline block: (param: MethodHookParam) -> Unit
) : XC_MethodHook {
    return object : XC_MethodHook(priority) {
        override fun beforeHookedMethod(param: MethodHookParam) {
            block(param)
        }
    }
}

inline fun afterHook(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    crossinline block: (param: MethodHookParam) -> Unit
) : XC_MethodHook {
    return object : XC_MethodHook(priority) {
        override fun afterHookedMethod(param: MethodHookParam) {
            block(param)
        }
    }
}

inline fun replaceHook(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    crossinline block: (param: Replacer) -> Any?
) : XC_MethodHook {
    return object : XC_MethodReplacement(priority) {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            return block { param }
        }
    }
}

inline fun Member.hookAfterMethod(crossinline hooker: Hooker) =
    hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Member.hookBeforeMethod(crossinline hooker: (MethodHookParam) -> Unit) =
    hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookBeforeMethod(
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = hookMethod(method, *args, object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
})

inline fun Class<*>.hookAfterMethod(
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = hookMethod(method, *args, object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
})

inline fun Class<*>.hookAfterMethod(
    priority: Int,
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = hookMethod(method, *args, object : XC_MethodHook(priority) {
    override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
})

inline fun Class<*>.replaceMethod(
    method: String?,
    vararg args: Any?,
    crossinline replacer: Replacer
) = hookMethod(method, *args, object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
})

fun Class<*>.hookAllMethods(methodName: String?, hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> =
    try {
        hookAllMethods(this, methodName, hooker)
    } catch (e: Throwable) {
        throw e
    }

inline fun Class<*>.hookBeforeAllMethods(methodName: String?, crossinline hooker: Hooker) =
    hookAllMethods(methodName, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookAfterAllMethods(methodName: String?, crossinline hooker: Hooker) =
    hookAllMethods(methodName, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.replaceAllMethods(methodName: String?, crossinline replacer: Replacer) =
    hookAllMethods(methodName, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
    })

fun Class<*>.hookConstructor(vararg args: Any?): XC_MethodHook.Unhook = try {
    findAndHookConstructor(this, *args)
} catch (e: Throwable) {
    throw e
}

inline fun Class<*>.hookBeforeConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookAfterConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.replaceConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

fun Class<*>.hookAllConstructors(hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> = try {
    hookAllConstructors(this, hooker)
} catch (e: Throwable) {
    throw e
}

inline fun Class<*>.hookAfterAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookBeforeAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.replaceAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

fun String.hookMethod(classLoader: ClassLoader, method: String?, vararg args: Any?) = try {
    findClass(classLoader).hookMethod(method, *args)
} catch (e: Throwable) {
    throw e
}

inline fun String.hookBeforeMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = try {
    findClass(classLoader).hookBeforeMethod(method, *args, hooker = hooker)
} catch (e: Throwable) {
    throw e
}

inline fun String.hookAfterMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = try {
    findClass(classLoader).hookAfterMethod(method, *args, hooker = hooker)
} catch (e: Throwable) {
    throw e
}

inline fun String.replaceMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline replacer: Replacer
) = try {
    findClass(classLoader).replaceMethod(method, *args, replacer = replacer)
} catch (e: Throwable) {
    throw e
}

fun MethodHookParam.invokeOriginalMethod(): Any? = invokeOriginalMethod(method, thisObject, args)

fun  MethodHookParam.invokeOriginalWithArgs(
    vararg args: Any?
): Any? = invokeOriginalMethod(method, thisObject, args)

inline fun <T, R> T.runCatchingOrNull(func: T.() -> R?) = try {
    func()
} catch (_: Throwable) {
    null
}

fun Any.getObjectField(field: String?): Any? = getObjectField(this, field)

fun Any.getObjectFieldOrNull(field: String?): Any? = runCatchingOrNull {
    getObjectField(this, field)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldAs(field: String?) = getObjectField(this, field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getObjectField(this, field) as T
}

fun Any.getIntField(field: String?) = getIntField(this, field)

fun Any.getIntFieldOrNull(field: String?) = runCatchingOrNull {
    getIntField(this, field)
}

fun Any.getLongField(field: String?) = getLongField(this, field)

fun Any.getLongFieldOrNull(field: String?) = runCatchingOrNull {
    getLongField(this, field)
}

fun Any.getBooleanFieldOrNull(field: String?) = runCatchingOrNull {
    getBooleanField(this, field)
}

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? =
    callMethod(this, methodName, *args)

fun Any.callMethodOrNull(methodName: String?, vararg args: Any?): Any? = runCatchingOrNull {
    callMethod(this, methodName, *args)
}

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? =
    callStaticMethod(this, methodName, *args)

fun Class<*>.callStaticMethodOrNull(methodName: String?, vararg args: Any?): Any? =
    runCatchingOrNull {
        callStaticMethod(this, methodName, *args)
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodAs(methodName: String?, vararg args: Any?) =
    callStaticMethod(this, methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodOrNullAs(methodName: String?, vararg args: Any?) =
    runCatchingOrNull {
        callStaticMethod(this, methodName, *args) as T
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldAs(field: String?) = getStaticObjectField(this, field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getStaticObjectField(this, field) as T
}

fun Class<*>.getStaticObjectField(field: String?): Any? = getStaticObjectField(this, field)

fun Class<*>.getStaticObjectFieldOrNull(field: String?): Any? = runCatchingOrNull {
    getStaticObjectField(this, field)
}

fun Class<*>.setStaticObjectField(field: String?, obj: Any?) = apply {
    setStaticObjectField(this, field, obj)
}

fun Class<*>.setStaticObjectFieldIfExist(field: String?, obj: Any?) = apply {
    try {
        setStaticObjectField(this, field, obj)
    } catch (ignored: Throwable) {
    }
}

inline fun <reified T> Class<*>.findFieldByExactType(): Field? =
    findFirstFieldByExactType(this, T::class.java)

fun Class<*>.findFieldByExactType(type: Class<*>): Field? =
    findFirstFieldByExactType(this, type)

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodAs(methodName: String?, vararg args: Any?) =
    callMethod(this, methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodOrNullAs(methodName: String?, vararg args: Any?) = runCatchingOrNull {
    callMethod(this, methodName, *args) as T
}

fun Any.callMethod(methodName: String?, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? =
    callMethod(this, methodName, parameterTypes, *args)

fun Any.callMethodOrNull(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? = runCatchingOrNull {
    callMethod(this, methodName, parameterTypes, *args)
}

fun Class<*>.callStaticMethod(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? = callStaticMethod(this, methodName, parameterTypes, *args)

fun Class<*>.callStaticMethodOrNull(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? = runCatchingOrNull {
    callStaticMethod(this, methodName, parameterTypes, *args)
}

fun String.findClass(classLoader: ClassLoader?): Class<*> = findClass(this, classLoader)

infix fun String.on(classLoader: ClassLoader?): Class<*> = findClass(this, classLoader)

fun String.findClassOrNull(classLoader: ClassLoader?): Class<*>? =
    findClassIfExists(this, classLoader)

infix fun String.from(classLoader: ClassLoader?): Class<*>? =
    findClassIfExists(this, classLoader)

fun Class<*>.new(vararg args: Any?): Any = newInstance(this, *args)

fun Class<*>.new(parameterTypes: Array<Class<*>>, vararg args: Any?): Any =
    newInstance(this, parameterTypes, *args)

fun Class<*>.findField(field: String?): Field = findField(this, field)

fun Class<*>.findFieldOrNull(field: String?): Field? = findFieldIfExists(this, field)

fun <T> T.setIntField(field: String?, value: Int) = apply {
    setIntField(this, field, value)
}

fun <T> T.setLongField(field: String?, value: Long) = apply {
    setLongField(this, field, value)
}

fun <T> T.setObjectField(field: String?, value: Any?) = apply {
    setObjectField(this, field, value)
}

fun <T> T.setBooleanField(field: String?, value: Boolean) = apply {
    setBooleanField(this, field, value)
}

fun <T> T.setFloatField(field: String?, value: Float) = apply {
    setFloatField(this, field, value)
}

inline fun XResources.hookLayout(
    id: Int,
    crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit
) {
    try {
        hookLayout(id, object : XC_LayoutInflated() {
            override fun handleLayoutInflated(liparam: LayoutInflatedParam) {
                try {
                    hooker(liparam)
                } catch (e: Throwable) {
                    Log.e("", e)
                }
            }
        })
    } catch (e: Throwable) {
        Log.e("", e)
    }
}

inline fun XResources.hookLayout(
    pkg: String,
    type: String,
    name: String,
    crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit
) {
    try {
        val id = getIdentifier(name, type, pkg)
        hookLayout(id, hooker)
    } catch (e: Throwable) {
        Log.e("", e)
    }
}

fun Class<*>.findFirstFieldByExactType(type: Class<*>): Field =
    findFirstFieldByExactType(this, type)

fun Class<*>.findFirstFieldByExactTypeOrNull(type: Class<*>?): Field? = runCatchingOrNull {
    findFirstFieldByExactType(this, type)
}

fun Any.getFirstFieldByExactType(type: Class<*>): Any? =
    javaClass.findFirstFieldByExactType(type).get(this)

@Suppress("UNCHECKED_CAST")
fun <T> Any.getFirstFieldByExactTypeAs(type: Class<*>) =
    javaClass.findFirstFieldByExactType(type).get(this) as? T

inline fun <reified T : Any> Any.getFirstFieldByExactType() =
    javaClass.findFirstFieldByExactType(T::class.java).get(this) as? T

fun Any.getFirstFieldByExactTypeOrNull(type: Class<*>?): Any? = runCatchingOrNull {
    javaClass.findFirstFieldByExactTypeOrNull(type)?.get(this)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getFirstFieldByExactTypeOrNullAs(type: Class<*>?) =
    getFirstFieldByExactTypeOrNull(type) as? T

inline fun <reified T> Any.getFirstFieldByExactTypeOrNull() =
    getFirstFieldByExactTypeOrNull(T::class.java) as? T

inline fun ClassLoader.findDexClassLoader(crossinline delegator: (BaseDexClassLoader) -> BaseDexClassLoader = { x -> x }): BaseDexClassLoader? {
    var classLoader = this
    while (classLoader !is BaseDexClassLoader) {
        if (classLoader.parent != null) classLoader = classLoader.parent
        else return null
    }
    return delegator(classLoader)
}

inline fun ClassLoader.allClassesList(crossinline delegator: (BaseDexClassLoader) -> BaseDexClassLoader = { x -> x }): List<String> {
    return findDexClassLoader(delegator)?.getObjectField("pathList")
        ?.getObjectFieldAs<Array<Any>>("dexElements")
        ?.flatMap {
            it.getObjectField("dexFile")?.callMethodAs<Enumeration<String>>("entries")?.toList()
                .orEmpty()
        }.orEmpty()
}

object FuzzyClassKit {
    private val dic = arrayOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
        "u", "v", "w", "x", "y", "z"
    )

    fun findMethodByClassPrefix(prefix: String, isSubClass: Boolean = false, check: (Class<*>, Method) -> Boolean): Method? {
        dic.forEach { className ->
            val clz = load("$prefix${if (isSubClass) "$" else "."}$className")
            clz?.declaredMethods?.forEach {
                if (check(clz, it)) return it
            }
        }

        return null
    }

    fun findMethodByClassName(prefix: String, check: (Class<*>, Method) -> Boolean): Method? {
        return dic.firstNotNullOfOrNull { outerClass ->
            val mainName = "$prefix.$outerClass"
            val mainClz = load(mainName) ?: return@firstNotNullOfOrNull null

            mainClz.declaredMethods.firstOrNull { check(mainClz, it) }
                ?:
                dic.firstNotNullOfOrNull { innerClass ->
                    val innerName = "$mainName$$innerClass"
                    val innerClz = load(innerName) ?: return@firstNotNullOfOrNull null
                    innerClz.declaredMethods.firstOrNull { check(innerClz, it) }
                }
        }
    }

    fun findClassByMethod(prefix: String, isSubClass: Boolean = false, check: (Class<*>, Method) -> Boolean): Class<*>? {
        dic.forEach { name ->
            val clz = load("$prefix${if (isSubClass) "$" else "."}$name")
            clz?.declaredMethods?.forEach {
                if (check(clz, it)) return clz
            }
        }

        return  null
    }
}

val Member.isStatic: Boolean
    inline get() = Modifier.isStatic(modifiers)
val Member.isNotStatic: Boolean
    inline get() = !isStatic

val Class<*>.isStatic: Boolean
    inline get() = Modifier.isStatic(modifiers)
val Class<*>.isNotStatic: Boolean
    inline get() = !this.isStatic

val Member.isPublic: Boolean
    inline get() = Modifier.isPublic(modifiers)
val Member.isNotPublic: Boolean
    inline get() = !this.isPublic

val Class<*>.isPublic: Boolean
    inline get() = Modifier.isPublic(modifiers)
val Class<*>.isNotPublic: Boolean
    inline get() = !this.isPublic

val Member.isProtected: Boolean
    inline get() = Modifier.isProtected(modifiers)
val Member.isNotProtected: Boolean
    inline get() = !this.isProtected

val Class<*>.isProtected: Boolean
    inline get() = Modifier.isProtected(modifiers)
val Class<*>.isNotProtected: Boolean
    inline get() = !this.isProtected

val Member.isPrivate: Boolean
    inline get() = Modifier.isPrivate(modifiers)
val Member.isNotPrivate: Boolean
    inline get() = !this.isPrivate

val Class<*>.isPrivate: Boolean
    inline get() = Modifier.isPrivate(modifiers)
val Class<*>.isNotPrivate: Boolean
    inline get() = !this.isPrivate

val Member.isFinal: Boolean
    inline get() = Modifier.isFinal(modifiers)
val Member.isNotFinal: Boolean
    inline get() = !this.isFinal

val Class<*>.isFinal: Boolean
    inline get() = Modifier.isFinal(modifiers)
val Class<*>.isNotFinal: Boolean
    inline get() = !this.isFinal

val Member.isNative: Boolean
    inline get() = Modifier.isNative(modifiers)
val Member.isNotNative: Boolean
    inline get() = !this.isNative

val Member.isSynchronized: Boolean
    inline get() = Modifier.isSynchronized(modifiers)
val Member.isNotSynchronized: Boolean
    inline get() = !this.isSynchronized

val Member.isAbstract: Boolean
    inline get() = Modifier.isAbstract(modifiers)
val Member.isNotAbstract: Boolean
    inline get() = !this.isAbstract

val Class<*>.isAbstract: Boolean
    inline get() = Modifier.isAbstract(modifiers)
val Class<*>.isNotAbstract: Boolean
    inline get() = !this.isAbstract

val Member.isTransient: Boolean
    inline get() = Modifier.isTransient(modifiers)
val Member.isNotTransient: Boolean
    inline get() = !this.isTransient

val Member.isVolatile: Boolean
    inline get() = Modifier.isVolatile(modifiers)
val Member.isNotVolatile: Boolean
    inline get() = !this.isVolatile

val Method.paramCount: Int
    inline get() = this.parameterTypes.size

val Constructor<*>.paramCount: Int
    inline get() = this.parameterTypes.size

val Method.emptyParam: Boolean
    inline get() = this.paramCount == 0
val Method.notEmptyParam: Boolean
    inline get() = this.paramCount != 0

val Constructor<*>.emptyParam: Boolean
    inline get() = this.paramCount == 0
val Constructor<*>.notEmptyParam: Boolean
    inline get() = this.paramCount != 0
