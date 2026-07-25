package com.owo233.tcqt.utils.reflect

import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import androidx.core.util.size
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.lang.ref.WeakReference
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

// ==================== Reflection Core ====================

private val fieldCache = ConcurrentHashMap<Pair<Class<*>, Boolean>, Array<Field>>()
private val methodCache = ConcurrentHashMap<Pair<Class<*>, Boolean>, Array<Method>>()
private val constructorCache = ConcurrentHashMap<Class<*>, Array<Constructor<*>>>()

private val prettyJson = Json {
    allowSpecialFloatingPointValues = true
    isLenient = true
    prettyPrint = true
}

private fun AccessibleObject.allowAccess() {
    makeAccessible()
}

private fun Class<*>.allFields(withSuper: Boolean): Array<Field> {
    return fieldCache.getOrPut(this to withSuper) {
        buildList {
            var current: Class<*>? = this@allFields
            while (current != null) {
                addAll(current.declaredFields)
                current = if (withSuper) current.superclass else null
            }
        }.toTypedArray()
    }
}

private fun Class<*>.allMethods(withSuper: Boolean): Array<Method> {
    return methodCache.getOrPut(this to withSuper) {
        val scope = if (withSuper) SearchScope.HIERARCHY else SearchScope.DECLARED
        collectSearchClasses(this, scope)
            .flatMap { it.declaredMethods.asList() }
            .distinct()
            .toTypedArray()
    }
}

fun Class<*>.allConstructors(): Array<Constructor<*>> {
    return constructorCache.getOrPut(this) {
        declaredConstructors.clone()
    }
}

fun Any.getMethods(withSuper: Boolean = true): Array<Method> {
    val clazz = reflectionTargetClass()
    return clazz.allMethods(withSuper).clone()
}

fun Any.getFields(withSuper: Boolean = true): Array<Field> {
    val clazz = reflectionTargetClass()
    return clazz.allFields(withSuper).clone()
}

fun Any.field(fieldType: Class<*>, withSuper: Boolean = true): Field? {
    return reflectionTargetClass().findFieldOrNull {
        type = fieldType
        scope = if (withSuper) SearchScope.SUPERCLASSES else SearchScope.DECLARED
        isStatic = if (this@field is Class<*>) true else false
    }
}

fun Any.field(fieldName: String, withSuper: Boolean = true): Field? {
    return reflectionTargetClass().findFieldOrNull {
        name = fieldName
        scope = if (withSuper) SearchScope.SUPERCLASSES else SearchScope.DECLARED
        isStatic = if (this@field is Class<*>) true else false
    }
}

fun Any.fieldValue(fieldType: Class<*>, withSuper: Boolean = true): Any? {
    val field = field(fieldType, withSuper) ?: return null
    return field.get(fieldReceiver(field))
}

fun Any.fieldValue(name: String, withSuper: Boolean = true): Any? {
    val field = field(name, withSuper) ?: return null
    return field.get(fieldReceiver(field))
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(fieldType: Class<*>, withSuper: Boolean = true): T? =
    fieldValue(fieldType, withSuper) as T?

@Suppress("UNCHECKED_CAST")
fun <T> Any.fieldValueAs(name: String, withSuper: Boolean = true): T? =
    fieldValue(name, withSuper) as T?

fun Any.invoke(method: Method, vararg args: Any?): Any? {
    val receiver = if (this is Class<*>) null else this
    return method.invokeReflectively(receiver, args)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.invokeAs(method: Method, vararg args: Any?): T {
    return invoke(method, *args) as T
}

fun <T> Any.invoke(
    name: String,
    returnType: Class<T>,
    vararg args: Any?,
    withSuper: Boolean = true
): T {
    val clazz = reflectionTargetClass()
    val method = clazz.resolveMethodForArguments(
        name = name,
        args = args,
        returnType = returnType,
        scope = if (withSuper) SearchScope.HIERARCHY else SearchScope.DECLARED,
        isStatic = this is Class<*>
    ) ?: throw NoSuchMethodException(
        "No matching method $name found in ${clazz.name}"
    )

    @Suppress("UNCHECKED_CAST")
    return method.invokeReflectively(if (this is Class<*>) null else this, args) as T
}

fun Any.invoke(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): Any? {
    val clazz = reflectionTargetClass()
    val method = clazz.resolveMethodForArguments(
        name = name,
        args = args,
        scope = if (withSuper) SearchScope.HIERARCHY else SearchScope.DECLARED,
        isStatic = this is Class<*>
    ) ?: throw NoSuchMethodException(
        "No matching method $name found in ${clazz.name}"
    )
    return method.invokeReflectively(if (this is Class<*>) null else this, args)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.invokeAs(
    name: String,
    vararg args: Any?,
    withSuper: Boolean = true
): T = invoke(name, *args, withSuper = withSuper) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<T>.new(vararg args: Any?): T {
    return newInstanceWithArgs(*args) as T
}

fun Any.setValue(name: String, value: Any?): Boolean {
    val field = field(name) ?: return false
    field.set(fieldReceiver(field), value)
    return true
}

fun Any.invokeMethod(
    withSuper: Boolean = false,
    vararg args: Any?,
    predicate: Method.() -> Boolean
): Any? {
    val clazz = reflectionTargetClass()
    val expectStatic = this is Class<*>
    val candidates = clazz.allMethods(withSuper).filter { method ->
        Modifier.isStatic(method.modifiers) == expectStatic && method.predicate()
    }
    val method = resolveBestMethodForArguments(candidates, args)
        ?: throw NoSuchMethodException(
            buildString {
                append("No matching method found in ").append(clazz.name)
                append(", args=")
                append(args.joinToString(prefix = "[", postfix = "]") {
                    when (it) {
                        null -> "null"
                        is TypedNull -> "null:${it.type.name}"
                        else -> it.javaClass.name
                    }
                })
            }
        )

    return method.invokeReflectively(if (expectStatic) null else this, args)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.invokeMethodAs(
    withSuper: Boolean = false,
    vararg args: Any?,
    predicate: Method.() -> Boolean
): T? {
    return invokeMethod(withSuper, *args, predicate = predicate) as T?
}

private fun Any.reflectionTargetClass(): Class<*> =
    (this as? Class<*>) ?: javaClass

private fun Any.fieldReceiver(field: Field): Any? {
    return if (Modifier.isStatic(field.modifiers)) null else {
        if (this is Class<*>) {
            throw IllegalArgumentException(
                "Cannot access instance field ${field.declaringClass.name}#${field.name} from Class target"
            )
        }
        this
    }
}

// ==================== JSON Serialization ====================

/**
 * 将任意对象转换为JSON格式字符串，通过反射获取所有字段。
 *
 * 支持的类型：
 * - 基本类型及其包装类、String、Char
 * - Enum（输出 name + ordinal）
 * - 数组（包括所有基本类型数组与对象数组）
 * - Iterable / Map / Sequence / Pair / Triple
 * - Date / Calendar / URI / URL / Class<*>
 * - Android: Bundle / SparseArray / Intent / WeakReference
 * - 任意对象：通过反射遍历所有实例字段递归序列化
 * - 循环引用检测（输出 @ref 标记）
 *
 * @param maxDepth 最大递归深度，防止循环引用导致栈溢出，默认3层
 * @param withSuper 是否包含父类字段，默认true
 * @param prettyPrint 是否格式化输出，默认false（紧凑JSON）
 * @return JSON格式字符串
 */
fun Any?.toJsonString(
    maxDepth: Int = 3,
    withSuper: Boolean = true,
    prettyPrint: Boolean = false
): String {
    val element = this.toJsonElement(maxDepth, withSuper, HashSet())

    return when {
        prettyPrint -> prettyJson.encodeToString(
            JsonElement.serializer(),
            element
        )
        else -> element.toString()
    }
}

// ==================== Element Conversion ====================

private fun Any?.toJsonElement(
    maxDepth: Int,
    withSuper: Boolean,
    visited: MutableSet<Int>
): JsonElement {
    if (this == null) return JsonNull

    when (this) {
        is Boolean -> return JsonPrimitive(this)
        is Number -> return JsonPrimitive(this)
        is Char -> return JsonPrimitive(toString())
        is String -> return JsonPrimitive(this)
        is CharSequence -> return JsonPrimitive(toString())
        is Enum<*> -> return buildJsonObject {
            put("_type", JsonPrimitive(javaClass.name))
            put("name", JsonPrimitive(name))
            put("ordinal", JsonPrimitive(ordinal))
        }
    }

    val objectId = System.identityHashCode(this)
    if (objectId in visited) return JsonPrimitive("@ref:0x${Integer.toHexString(objectId)}")
    if (maxDepth <= 0) return JsonPrimitive(this.safeSummary())

    visited.add(objectId)
    try {
        val result = when (this) {
            is Date -> buildJsonObject {
                put("_type", JsonPrimitive("java.util.Date"))
                put("time", JsonPrimitive(time))
                put("_value", JsonPrimitive(this@toJsonElement.toString()))
            }
            is Calendar -> buildJsonObject {
                put("_type", JsonPrimitive("java.util.Calendar"))
                put("timeInMillis", JsonPrimitive(timeInMillis))
                put("_value", JsonPrimitive(this@toJsonElement.toString()))
            }
            is URI -> JsonPrimitive(toString())
            is URL -> JsonPrimitive(toString())
            is Class<*> -> JsonPrimitive(name)

            is Pair<*, *> -> buildJsonArray {
                add(first.toJsonElement(maxDepth - 1, withSuper, visited))
                add(second.toJsonElement(maxDepth - 1, withSuper, visited))
            }
            is Triple<*, *, *> -> buildJsonArray {
                add(first.toJsonElement(maxDepth - 1, withSuper, visited))
                add(second.toJsonElement(maxDepth - 1, withSuper, visited))
                add(third.toJsonElement(maxDepth - 1, withSuper, visited))
            }

            // ========== 以下集合类型不消耗深度 ==========
            is Map<*, *> -> buildJsonObject {
                this@toJsonElement.forEach { (k, v) ->
                    put(k.toString(), v.toJsonElement(maxDepth, withSuper, visited))
                }
            }
            is Iterable<*> -> buildJsonArray {
                this@toJsonElement.forEach {
                    add(it.toJsonElement(maxDepth, withSuper, visited))
                }
            }
            is Sequence<*> -> buildJsonArray {
                this@toJsonElement.forEach {
                    add(it.toJsonElement(maxDepth, withSuper, visited))
                }
            }
            is Array<*> -> buildJsonArray {
                this@toJsonElement.forEach {
                    add(it.toJsonElement(maxDepth, withSuper, visited))
                }
            }

            is BooleanArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is ByteArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is ShortArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is IntArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is LongArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is FloatArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is DoubleArray -> buildJsonArray { forEach { add(JsonPrimitive(it)) } }
            is CharArray -> buildJsonArray { forEach { add(JsonPrimitive(it.toString())) } }

            is Bundle -> buildJsonObject {
                put("_type", JsonPrimitive("android.os.Bundle"))
                for (key in this@toJsonElement.keySet()) {
                    @Suppress("DEPRECATION")
                    put(key, this@toJsonElement.get(key).toJsonElement(maxDepth, withSuper, visited))
                }
            }
            is SparseArray<*> -> buildJsonObject {
                put("_type", JsonPrimitive("android.util.SparseArray"))
                for (i in 0 until this@toJsonElement.size) {
                    put(
                        this@toJsonElement.keyAt(i).toString(),
                        this@toJsonElement.valueAt(i).toJsonElement(maxDepth, withSuper, visited))
                }
            }

            is Intent -> buildJsonObject {
                put("_type", JsonPrimitive("android.content.Intent"))
                action?.let { put("action", JsonPrimitive(it)) }
                data?.let { put("data", JsonPrimitive(it.toString())) }
                type?.let { put("type", JsonPrimitive(it)) }
                `package`?.let { put("package", JsonPrimitive(it)) }
                component?.let { put("component", JsonPrimitive(it.flattenToString())) }
                val flags = flags
                if (flags != 0) put("flags", JsonPrimitive("0x${Integer.toHexString(flags)}"))
                if (extras != null) {
                    put("extras", extras.toJsonElement(maxDepth - 1, withSuper, visited))
                }
            }

            is WeakReference<*> -> {
                val target = this.get()
                target?.toJsonElement(maxDepth - 1, withSuper, visited)
                    ?: JsonPrimitive("<cleared>")
            }

            else -> reflectObject(this, maxDepth, withSuper, visited)
        }
        return result
    } catch (e: Exception) {
        return JsonPrimitive("<error: ${e.javaClass.simpleName}: ${e.message}>")
    } finally {
        visited.remove(objectId)
    }
}

private fun Any.safeSummary(): String {
    return try {
        "<${javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}>"
    } catch (_: Exception) {
        "<${javaClass.name}>"
    }
}

private fun reflectObject(
    obj: Any,
    maxDepth: Int,
    withSuper: Boolean,
    visited: MutableSet<Int>
): JsonObject = buildJsonObject {
    val clazz = obj.javaClass

    // Skip JDK/Android internal types — reflection is unsafe/unhelpful
    val n = clazz.name
    if (n.startsWith("java.") || n.startsWith("android.") ||
        n.startsWith("kotlin.") || n.startsWith("kotlinx.")
    ) {
        put("_class", JsonPrimitive(n))
        put("_value", JsonPrimitive(obj.toString()))
        return@buildJsonObject
    }

    put("_class", JsonPrimitive(n))
    put("_hash", JsonPrimitive("0x${Integer.toHexString(System.identityHashCode(obj))}"))

    try {
        obj.getFields(withSuper).forEach { field ->
            // Skip static, synthetic, shadow$ (Roblectric), this$ (outer ref),
            // Companion singletons, Kotlin object INSTANCE
            val mod = field.modifiers
            if (Modifier.isStatic(mod) || field.isSynthetic) return@forEach
            val name = field.name
            if (name.startsWith("shadow$") || name.startsWith("this$") ||
                name == "INSTANCE" || name == "Companion"
            ) return@forEach

            field.allowAccess()
            try {
                val value = field.get(obj)
                put(name, value.toJsonElement(maxDepth - 1, withSuper, visited))
            } catch (_: Exception) {
                put(name, JsonPrimitive("<ACCESS_DENIED>"))
            }
        }
    } catch (e: Exception) {
        put("_error", JsonPrimitive("${e.javaClass.simpleName}: ${e.message}"))
    }
}
