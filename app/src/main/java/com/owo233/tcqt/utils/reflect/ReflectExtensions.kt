package com.owo233.tcqt.utils.reflect

import com.owo233.tcqt.loader.api.HookEngineManager
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val Any.TAG: String
    get() = this.javaClass.simpleName

fun Member.callOriginal(obj: Any?, vararg args: Any?): Any? {
    return HookEngineManager.engine.getInvoker(this).invokeOrigin(obj, *args)
}

fun Any.callMethod(methodName: String, vararg args: Any?): Any? {
    return this.javaClass.findMethodAndCall(
        receiver = this,
        methodName = methodName,
        args = args,
        isStatic = false
    )
}

fun Class<*>.callStaticMethod(methodName: String, vararg args: Any?): Any? {
    return findMethodAndCall(
        receiver = null,
        methodName = methodName,
        args = args,
        isStatic = true
    )
}

private fun Class<*>.findMethodAndCall(
    receiver: Any?,
    methodName: String,
    args: Array<out Any?>,
    isStatic: Boolean
): Any? {
    val method = resolveMethodForArguments(
        name = methodName,
        args = args,
        scope = SearchScope.HIERARCHY,
        isStatic = isStatic
    ) ?: throw NoSuchMethodException(
        "Method $methodName not found in $name with args: ${formatArgumentTypes(args)}"
    )

    return method.invokeReflectively(receiver, args)
}

internal fun Method.invokeReflectively(
    receiver: Any?,
    args: Array<out Any?>
): Any? {
    makeAccessible()
    val actualReceiver = if (Modifier.isStatic(modifiers)) null else receiver
    val invocationArgs = prepareInvocationArguments(this, args)

    return try {
        invoke(actualReceiver, *invocationArgs)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    } catch (_: IllegalAccessException) {
        // 部分 Hook 引擎场景下普通反射调用会被访问控制拒绝，此时才尝试引擎调用。
        HookEngineManager.engine.getInvoker(this).invokeOrigin(actualReceiver, *invocationArgs)
    }
}

fun Any.getObjectByTypeOrNull(type: Class<*>, inParent: Class<*>? = null): Any? {
    val field = this.javaClass.findFieldOrNull {
        this.type = type
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    } ?: return null
    return field.get(this)
}

inline fun <reified T> Any.getObjectByTypeOrNull(inParent: Class<*>? = null): T? {
    return getObjectByTypeOrNull(T::class.java, inParent) as? T
}

inline fun <reified T> Any.getObjectByType(inParent: Class<*>? = null): T {
    val field = this.javaClass.findFieldOrNull {
        type = T::class.java
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    } ?: throw NoSuchFieldException(
        "Field of type ${T::class.java.name} not found in ${this.javaClass.name}"
    )

    @Suppress("UNCHECKED_CAST")
    return field.get(this) as? T
        ?: throw NullPointerException("Field ${field.name} in ${field.declaringClass.name} is null")
}

fun Any.getObjectOrNull(name: String, inParent: Class<*>? = null): Any? {
    val field = this.javaClass.findFieldOrNull {
        this.name = name
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    } ?: return null
    return field.get(this)
}

fun Any.getObject(name: String, inParent: Class<*>? = null): Any {
    val field = this.javaClass.findFieldOrNull {
        this.name = name
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    } ?: throw NoSuchFieldException("Field '$name' not found in ${this.javaClass.name}")

    return field.get(this)
        ?: throw NullPointerException("Field '$name' in ${field.declaringClass.name} is null")
}

fun Any.setObject(name: String, value: Any?, inParent: Class<*>? = null) {
    this.javaClass.findField {
        this.name = name
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    }.set(this, value)
}

inline fun <reified T> Any.setObjectByType(value: T?, inParent: Class<*>? = null) {
    this.javaClass.findField {
        type = T::class.java
        this.inParent = inParent
        scope = if (inParent == null) SearchScope.HIERARCHY else SearchScope.DECLARED
        isStatic = false
    }.set(this, value)
}

fun Class<*>.getStaticObject(name: String): Any? {
    return findField {
        this.name = name
        isStatic = true
        scope = SearchScope.HIERARCHY
    }.get(null)
}

fun Class<*>.newInstanceWithArgs(vararg args: Any?): Any {
    val constructor = resolveConstructorForArguments(args)
        ?: throw NoSuchMethodException(
            "No constructor found for $name with args: ${formatArgumentTypes(args)}"
        )

    val invocationArgs = prepareInvocationArguments(constructor, args)
    return try {
        constructor.newInstance(*invocationArgs)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }
}

/**
 * 类型兼容判断，包含引用赋值兼容及基本类型与包装类型的等价关系。
 * [actualType] 为 null 时保留旧 DSL 语义，表示任意类型。
 */
fun Class<*>.isCompatibleWith(actualType: Class<*>?): Boolean {
    return actualType == null || ReflectTypeMatcher.isTypeCompatible(this, actualType)
}

private fun formatArgumentTypes(args: Array<out Any?>): String {
    return args.joinToString(prefix = "[", postfix = "]") { value ->
        when (value) {
            null -> "null"
            is TypedNull -> "null:${value.type.name}"
            else -> value.javaClass.name
        }
    }
}
