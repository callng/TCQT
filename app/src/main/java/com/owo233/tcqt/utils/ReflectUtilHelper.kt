package com.owo233.tcqt.utils

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 反射工具类，提供对字段和方法的安全、高效访问。
 *
 * 该对象封装了常用的 Java 反射操作，支持调用实例/静态方法、获取实例/静态字段，并通过内部缓存机制
 * 提升重复反射调用的性能。所有访问均自动设置 `AccessibleObject.isAccessible` 为 `true`，
 * 可访问 `private`、`protected` 等非公开成员。
 *
 * ## 注意事项
 * 1. **仅查找当前类声明的成员**：不搜索父类或接口中的方法/字段。若需访问继承成员，请传入实际声明的类。
 * 2. **参数类型必须精确匹配**：包括基本类型（如 `int.class`）与包装类（如 `Integer.class`）的区分。
 * 3. **Kotlin 属性**：Kotlin 的 `val`/`var` 属性需通过其生成的字段或 getter 方法访问，本工具仅支持 Java 反射层面操作。
 * 4. **类型转换不强制校验**：`as? T` 依赖运行时类型，若预期类型与实际返回类型不符，可能返回 `null`。
 *
 * @see invokeMethod
 * @see invokeStaticMethod
 * @see getFieldValue
 * @see getStaticField
 */
object ReflectUtilHelper {

    private val methodCache = ConcurrentHashMap<String, Method>()
    private val fieldCache = ConcurrentHashMap<String, Field>()

    /**
     * 调用目标对象的指定实例方法。
     *
     * 查找并调用 `target` 对象中名为 [methodName] 的方法，支持任意访问修饰符（包括 `private`）。
     * 方法的参数类型由 [parameterTypes] 指定，必须与目标方法签名**完全匹配**。
     *
     * @param T 期望的返回值类型，自动尝试转换。若类型不匹配或方法返回 `null`，则返回 `null`。
     * @param target 要调用方法的目标对象，不能为 `null`。
     * @param methodName 方法名称，必须存在于 `target.javaClass` 中。
     * @param parameterTypes 方法参数类型的数组，默认为空数组（表示无参方法）。
     *                       传入 `null` 元素将导致 [IllegalArgumentException]。
     * @param args 方法调用时传入的实际参数，数量和类型应与 [parameterTypes] 匹配。
     * @return 方法执行结果，转换为类型 `T`，失败或异常时返回 `null`。
     *
     * @throws RuntimeException 反射过程中发生错误时，内部捕获并记录日志，不向外抛出。
     *
     * @sample
     *   val result: String? = ReflectUtils.invokeMethod<String>(myObj, "getSecret", arrayOf(String::class.java), "key")
     */
    inline fun <reified T> invokeMethod(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        vararg args: Any?
    ): T? = try {
        val method = getMethodCached(target.javaClass, methodName, parameterTypes)
        method.invoke(target, *args) as? T
    } catch (e: Throwable) {
        logE("ReflectUtils", "invokeMethod error: $methodName", e)
        null
    }

    /**
     * 调用指定类的静态方法。
     *
     * 查找并调用 [clazz] 中名为 [methodName] 的静态方法，支持任意访问修饰符。
     *
     * @param T 期望的返回值类型，自动尝试转换。若类型不匹配或方法返回 `null`，则返回 `null`。
     * @param clazz 目标方法所在类的 [Class] 对象，不能为 `null`。
     * @param methodName 静态方法名称，必须在 [clazz] 中声明。
     * @param parameterTypes 方法参数类型数组，默认为空数组。
     * @param args 调用时传入的参数。
     * @return 方法执行结果，转换为 `T`，失败时返回 `null`。
     *
     * @sample
     *   val value: Int? = ReflectUtils.invokeStaticMethod<Int>(MyClass::class.java, "computeSum", arrayOf(Int::class.java, Int::class.java), 3, 4)
     */
    inline fun <reified T> invokeStaticMethod(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        vararg args: Any?
    ): T? = try {
        val method = getMethodCached(clazz, methodName, parameterTypes)
        method.invoke(null, *args) as? T
    } catch (e: Throwable) {
        logE("ReflectUtils", "invokeStaticMethod error: $methodName", e)
        null
    }

    /**
     * 获取目标对象中指定实例字段的值。
     *
     * 访问 `target` 对象中名为 [fieldName] 的字段，无论其访问级别如何。
     *
     * @param T 期望的字段值类型，自动尝试转换。若类型不匹配或字段值为 `null`，则返回 `null`。
     * @param target 字段所属的对象实例，不能为 `null`。
     * @param fieldName 字段名称，必须在 `target.javaClass` 中声明。
     * @return 字段当前值，转换为 `T`，失败时返回 `null`。
     *
     * @sample
     *   val secret: String? = ReflectUtils.getFieldValue<String>(myObj, "secretMessage")
     */
    inline fun <reified T> getFieldValue(
        target: Any,
        fieldName: String
    ): T? = try {
        val field = getFieldCached(target.javaClass, fieldName)
        field.get(target) as? T
    } catch (e: Throwable) {
        logE("ReflectUtils", "getFieldValue error: $fieldName", e)
        null
    }

    /**
     * 获取指定类的静态字段的值。
     *
     * 访问 [clazz] 中名为 [fieldName] 的静态字段。
     *
     * @param T 期望的字段值类型。
     * @param clazz 字段所属的类。
     * @param fieldName 静态字段名称。
     * @return 字段值，转换为 `T`，失败时返回 `null`。
     *
     * @sample
     *   val version: String? = ReflectUtils.getStaticField<String>(Config::class.java, "APP_VERSION")
     */
    inline fun <reified T> getStaticField(
        clazz: Class<*>,
        fieldName: String
    ): T? = try {
        val field = getFieldCached(clazz, fieldName)
        field.get(null) as? T
    } catch (e: Throwable) {
        logE("ReflectUtils", "getStaticField error: $fieldName", e)
        null
    }

    /**
     * 获取指定类中声明的方法，并缓存以提高后续调用性能。
     *
     * 使用类名、方法名和参数类型签名作为缓存键。方法查找使用 [Class.getDeclaredMethod]，
     * 因此**不会搜索父类**。
     *
     * @param clazz 方法所在的类。
     * @param methodName 方法名称。
     * @param parameterTypes 方法参数类型数组，用于精确匹配重载方法。
     * @return 可访问的 [Method] 对象。
     * @throws NoSuchMethodException 若方法未找到。
     *
     * @see methodCache
     */
    @PublishedApi
    internal fun getMethodCached(clazz: Class<*>, methodName: String, parameterTypes: Array<Class<*>>): Method {
        val paramTypesStr = parameterTypes.joinToString(",") { it.name }
        val key = "${clazz.name}#$methodName($paramTypesStr)"
        return methodCache.getOrPut(key) {
            clazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
        }
    }

    /**
     * 获取指定类中声明的字段，并缓存以提高后续访问性能。
     *
     * 使用类名和字段名作为缓存键。字段查找使用 [Class.getDeclaredField]，
     * 因此**不会搜索父类**。
     *
     * @param clazz 字段所在的类。
     * @param fieldName 字段名称。
     * @return 可访问的 [Field] 对象。
     * @throws NoSuchFieldException 若字段未找到。
     *
     * @see fieldCache
     */
    @PublishedApi
    internal fun getFieldCached(clazz: Class<*>, fieldName: String): Field {
        val key = "${clazz.name}#$fieldName"
        return fieldCache.getOrPut(key) {
            clazz.getDeclaredField(fieldName).apply { isAccessible = true }
        }
    }
}
