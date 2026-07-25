package com.owo233.tcqt.utils.reflect

import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque

/** 成员搜索范围。 */
enum class SearchScope {
    /** 只搜索起始类直接声明的成员。 */
    DECLARED,

    /** 搜索起始类及其父类，不搜索接口。 */
    SUPERCLASSES,

    /** 搜索起始类、父类及实现的全部接口。 */
    HIERARCHY
}

enum class Visibility {
    PUBLIC,
    PROTECTED,
    PRIVATE,
    PACKAGE
}

class AmbiguousMethodException(message: String) : ReflectiveOperationException(message)

class AmbiguousFieldException(message: String) : ReflectiveOperationException(message)

/**
 * 用于在运行时调用中表达带类型的 null，解决多个引用类型重载无法判断的问题。
 */
class TypedNull internal constructor(internal val type: Class<*>) {
    init {
        require(!type.isPrimitive) { "Typed null cannot use primitive type: ${type.name}" }
    }
}

fun typedNull(type: Class<*>): TypedNull = TypedNull(type)

inline fun <reified T> typedNull(): TypedNull = typedNull(T::class.java)

/** 清空全部反射搜索缓存。 */
fun clearReflectCache() = ReflectCache.clear()

/** 只清理以当前类作为搜索入口的缓存。 */
fun Class<*>.clearReflectCache() = ReflectCache.clear(this)

internal sealed class ArgumentSpec {
    object AnyType : ArgumentSpec()
    object NullValue : ArgumentSpec()
    data class Typed(val type: Class<*>) : ArgumentSpec()
    data class TypedNullValue(val type: Class<*>) : ArgumentSpec()
}

private data class MethodCacheKey(
    override val owner: Class<*>,
    val startClass: Class<*>,
    val name: String?,
    val returnType: Class<*>?,
    val arguments: List<ArgumentSpec>?,
    val parameterCount: Int?,
    val isStatic: Boolean?,
    val visibility: Visibility?,
    val scope: SearchScope,
    val includeSynthetic: Boolean,
    val includeBridge: Boolean,
    val includeVarArgs: Boolean,
    val sameNameTypeMatch: Boolean,
    val index: Int,
    val requireUnique: Boolean
) : ReflectCache.CacheKey

private data class FieldCacheKey(
    override val owner: Class<*>,
    val startClass: Class<*>,
    val name: String?,
    val type: Class<*>?,
    val isStatic: Boolean?,
    val visibility: Visibility?,
    val scope: SearchScope,
    val includeSynthetic: Boolean,
    val sameNameTypeMatch: Boolean,
    val preferInstance: Boolean,
    val index: Int,
    val requireUnique: Boolean
) : ReflectCache.CacheKey

private data class ConstructorCacheKey(
    override val owner: Class<*>,
    val arguments: List<ArgumentSpec>,
    val includeVarArgs: Boolean
) : ReflectCache.CacheKey

private data class ExecutableScore(
    val totalCost: Int,
    val maxCost: Int,
    val varArgsPenalty: Int,
    val declaringDistance: Int,
    val syntheticPenalty: Int,
    val bridgePenalty: Int
) : Comparable<ExecutableScore> {
    override fun compareTo(other: ExecutableScore): Int {
        return compareValuesBy(
            this,
            other,
            ExecutableScore::totalCost,
            ExecutableScore::maxCost,
            ExecutableScore::varArgsPenalty,
            ExecutableScore::declaringDistance,
            ExecutableScore::syntheticPenalty,
            ExecutableScore::bridgePenalty
        )
    }
}

private data class ScoredMethod(
    val method: Method,
    val score: ExecutableScore
)

private data class ScoredConstructor(
    val constructor: Constructor<*>,
    val score: ExecutableScore
)

/** Java 调用转换及字段/返回值类型匹配的唯一实现。 */
internal object ReflectTypeMatcher {

    private val primitiveToWrapper = mapOf(
        Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType,
        Byte::class.javaPrimitiveType to Byte::class.javaObjectType,
        Short::class.javaPrimitiveType to Short::class.javaObjectType,
        Char::class.javaPrimitiveType to Char::class.javaObjectType,
        Int::class.javaPrimitiveType to Int::class.javaObjectType,
        Long::class.javaPrimitiveType to Long::class.javaObjectType,
        Float::class.javaPrimitiveType to Float::class.javaObjectType,
        Double::class.javaPrimitiveType to Double::class.javaObjectType,
        Void::class.javaPrimitiveType to Void::class.javaObjectType
    )

    private val wrapperToPrimitive = primitiveToWrapper.entries.associate { (primitive, wrapper) ->
        wrapper to primitive
    }

    fun boxed(type: Class<*>): Class<*> = primitiveToWrapper[type] ?: type

    fun unboxed(type: Class<*>): Class<*>? = when {
        type.isPrimitive -> type
        else -> wrapperToPrimitive[type]
    }

    fun isTypeCompatible(
        expected: Class<*>,
        actual: Class<*>,
        sameNameFallback: Boolean = false
    ): Boolean {
        if (expected === actual || expected == actual) return true

        val boxedExpected = boxed(expected)
        val boxedActual = boxed(actual)
        if (boxedExpected == boxedActual) return true
        if (boxedExpected.isAssignableFrom(boxedActual)) return true

        return sameNameFallback && expected.name == actual.name
    }

    fun conversionCost(
        parameterType: Class<*>,
        argument: ArgumentSpec,
        sameNameFallback: Boolean = false
    ): Int? {
        return when (argument) {
            ArgumentSpec.AnyType -> 512
            ArgumentSpec.NullValue -> if (parameterType.isPrimitive) null else 256
            is ArgumentSpec.TypedNullValue -> {
                if (parameterType.isPrimitive) return null
                referenceConversionCost(parameterType, argument.type, sameNameFallback)
            }
            is ArgumentSpec.Typed -> invocationConversionCost(
                parameterType,
                argument.type,
                sameNameFallback
            )
        }
    }

    private fun invocationConversionCost(
        parameterType: Class<*>,
        argumentType: Class<*>,
        sameNameFallback: Boolean
    ): Int? {
        if (parameterType == argumentType) return 0

        val parameterPrimitive = unboxed(parameterType)
        val argumentPrimitive = unboxed(argumentType)

        if (parameterPrimitive != null && argumentPrimitive != null) {
            if (parameterPrimitive == argumentPrimitive) return 1
            primitiveWideningDistance(argumentPrimitive, parameterPrimitive)?.let {
                return 2 + it
            }
        }

        return referenceConversionCost(parameterType, argumentType, sameNameFallback)
    }

    private fun referenceConversionCost(
        parameterType: Class<*>,
        argumentType: Class<*>,
        sameNameFallback: Boolean
    ): Int? {
        val expected = boxed(parameterType)
        val actual = boxed(argumentType)

        if (expected == actual) return 1
        if (expected.isAssignableFrom(actual)) {
            return 16 + (referenceDistance(actual, expected) ?: 64)
        }
        if (sameNameFallback && expected.name == actual.name) return 384
        return null
    }

    private fun primitiveWideningDistance(from: Class<*>, to: Class<*>): Int? {
        if (from == to) return 0
        val targets = when (from) {
            java.lang.Byte.TYPE -> listOf(
                java.lang.Short.TYPE,
                Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Short.TYPE -> listOf(
                Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            Character.TYPE -> listOf(
                Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            Integer.TYPE -> listOf(
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Long.TYPE -> listOf(java.lang.Float.TYPE, java.lang.Double.TYPE)
            java.lang.Float.TYPE -> listOf(java.lang.Double.TYPE)
            else -> emptyList()
        }
        val index = targets.indexOf(to)
        return if (index >= 0) index + 1 else null
    }

    private fun referenceDistance(actual: Class<*>, expected: Class<*>): Int? {
        if (actual == expected) return 0
        if (!expected.isAssignableFrom(actual)) return null

        val queue = ArrayDeque<Pair<Class<*>, Int>>()
        val visited = HashSet<Class<*>>()
        queue.add(actual to 0)

        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()
            if (!visited.add(current)) continue
            if (current == expected) return distance

            current.superclass?.let { queue.add(it to distance + 1) }
            current.interfaces.forEach { queue.add(it to distance + 1) }
        }
        return null
    }
}

class MethodSearcher internal constructor(private val ownerClass: Class<*>) {

    var name: String? = null
    var returnType: Class<*>? = null

    /** null 元素在 DSL 中表示该位置接受任意参数类型。 */
    var paramTypes: Array<out Class<*>?>? = null

    var paramCount: Int? = null
    var isStatic: Boolean? = null
    var visibility: Visibility? = null

    /** 默认搜索当前类、父类和接口。 */
    var scope: SearchScope = SearchScope.HIERARCHY

    /** 从指定父类或接口开始搜索。 */
    var inParent: Class<*>? = null

    /** 桥接方法过滤，默认不过滤。 */
    var includeSynthetic: Boolean = true
    /** 合成方法过滤，默认过滤。 */
    var includeBridge: Boolean = false
    var includeVarArgs: Boolean = true

    /** 允许不同 ClassLoader 中的同名类型弱匹配，默认关闭。 */
    var sameNameTypeMatch: Boolean = false

    /** 对排序后的候选取第几个，默认第一个。 */
    var index: Int = 0

    /** 多个候选满足条件时强制抛出歧义异常。 */
    var requireUnique: Boolean = false

    internal var argumentSpecs: List<ArgumentSpec>? = null

    val private get() = Visibility.PRIVATE
    val public get() = Visibility.PUBLIC
    val protected get() = Visibility.PROTECTED
    val pkg get() = Visibility.PACKAGE

    val declared get() = SearchScope.DECLARED
    val superclasses get() = SearchScope.SUPERCLASSES
    val hierarchy get() = SearchScope.HIERARCHY

    val void: Class<*> get() = Void.TYPE
    val boolean: Class<*> get() = java.lang.Boolean.TYPE
    val byte: Class<*> get() = java.lang.Byte.TYPE
    val short: Class<*> get() = java.lang.Short.TYPE
    val int: Class<*> get() = Integer.TYPE
    val intent: Class<*> get() = android.content.Intent::class.java
    val long: Class<*> get() = java.lang.Long.TYPE
    val float: Class<*> get() = java.lang.Float.TYPE
    val double: Class<*> get() = java.lang.Double.TYPE
    val char: Class<*> get() = Character.TYPE

    val string: Class<*> get() = String::class.java
    val obj: Class<*> get() = Any::class.java
    val map: Class<*> get() = Map::class.java
    val hashMap: Class<*> get() = HashMap::class.java
    val list: Class<*> get() = List::class.java
    val arrayList: Class<*> get() = ArrayList::class.java
    val set: Class<*> get() = Set::class.java
    val context: Class<*> get() = android.content.Context::class.java
    val bundle: Class<*> get() = android.os.Bundle::class.java
    val view: Class<*> get() = android.view.View::class.java

    val byteArr: Class<*> get() = ByteArray::class.java
    val intArr: Class<*> get() = IntArray::class.java
    val longArr: Class<*> get() = LongArray::class.java
    val floatArr: Class<*> get() = FloatArray::class.java
    val stringArr: Class<*> get() = Array<String>::class.java
    val objArr: Class<*> get() = Array<Any>::class.java

    fun paramTypes(vararg types: Class<*>?) {
        this.paramTypes = types
        this.argumentSpecs = null
    }

    fun params(vararg types: Class<*>?) = paramTypes(*types)

    internal val startClass: Class<*>
        get() = inParent ?: ownerClass

    internal fun validate() {
        require(index >= 0) { "Method index cannot be negative: $index" }
        val start = inParent ?: return
        require(start.isAssignableFrom(ownerClass)) {
            "${start.name} is not a parent class or interface of ${ownerClass.name}"
        }
    }

    internal fun resolvedArguments(): List<ArgumentSpec>? {
        argumentSpecs?.let { return it }
        return paramTypes?.map { type ->
            if (type == null) ArgumentSpec.AnyType else ArgumentSpec.Typed(type)
        }
    }

    internal fun cacheKey(): ReflectCache.CacheKey {
        validate()
        return MethodCacheKey(
            owner = ownerClass,
            startClass = startClass,
            name = name,
            returnType = returnType,
            arguments = resolvedArguments(),
            parameterCount = paramCount,
            isStatic = isStatic,
            visibility = visibility,
            scope = scope,
            includeSynthetic = includeSynthetic,
            includeBridge = includeBridge,
            includeVarArgs = includeVarArgs,
            sameNameTypeMatch = sameNameTypeMatch,
            index = index,
            requireUnique = requireUnique
        )
    }

    internal fun describe(): String = buildString {
        append("owner=").append(ownerClass.name)
        append(", start=").append(startClass.name)
        append(", name=").append(name ?: "*")
        append(", params=")
        append(resolvedArguments()?.joinToString(prefix = "[", postfix = "]") {
            when (it) {
                ArgumentSpec.AnyType -> "*"
                ArgumentSpec.NullValue -> "null"
                is ArgumentSpec.Typed -> it.type.name
                is ArgumentSpec.TypedNullValue -> "null:${it.type.name}"
            }
        } ?: "*")
        append(", return=").append(returnType?.name ?: "*")
        append(", count=").append(paramCount ?: "*")
        append(", static=").append(isStatic ?: "*")
        append(", visibility=").append(visibility ?: "*")
        append(", scope=").append(scope)
        append(", index=").append(index)
    }
}

fun Class<*>.findMethodOrNull(block: MethodSearcher.() -> Unit): Method? {
    val searcher = MethodSearcher(this).apply(block)
    return ReflectCache.getMethod(searcher.cacheKey()) {
        resolveMethod(searcher)
    }
}

fun Class<*>.findMethod(block: MethodSearcher.() -> Unit): Method {
    val searcher = MethodSearcher(this).apply(block)
    return ReflectCache.getMethod(searcher.cacheKey()) {
        resolveMethod(searcher)
    } ?: throw NoSuchMethodException("Method match failed: ${searcher.describe()}")
}

fun Class<*>.findMethods(block: MethodSearcher.() -> Unit): List<Method> {
    val searcher = MethodSearcher(this).apply(block)
    searcher.validate()
    return scoreMethods(searcher)
        .sortedWith(scoredMethodComparator)
        .map { it.method.makeAccessible() }
}

private fun resolveMethod(searcher: MethodSearcher): Method? {
    val scored = scoreMethods(searcher).sortedWith(scoredMethodComparator)
    if (scored.isEmpty()) return null

    if (searcher.requireUnique && scored.size > 1) {
        throw ambiguousMethod(searcher, scored.map { it.method })
    }

    if (searcher.index > 0) {
        return scored.getOrNull(searcher.index)?.method?.makeAccessible()
    }

    val bestScore = scored.first().score
    val tied = scored.takeWhile { it.score == bestScore }
    val selected = when {
        tied.size == 1 -> tied.first().method
        searcher.resolvedArguments() != null -> selectMostSpecific(tied.map { it.method })
        else -> null
    }

    if (selected != null) return selected.makeAccessible()
    if (tied.size > 1 && searcher.argumentSpecs != null) {
        throw ambiguousMethod(searcher, tied.map { it.method })
    }

    return scored.first().method.makeAccessible()
}

private fun scoreMethods(searcher: MethodSearcher): List<ScoredMethod> {
    val classes = collectSearchClasses(searcher.startClass, searcher.scope)
    val distance = classes.withIndex().associate { it.value to it.index }
    val args = searcher.resolvedArguments()
    val result = ArrayList<ScoredMethod>()

    for (clazz in classes) {
        for (method in clazz.declaredMethods) {
            if (searcher.name != null && method.name != searcher.name) continue
            if (!searcher.includeSynthetic && method.isSynthetic) continue
            if (!searcher.includeBridge && method.isBridge) continue
            if (!searcher.includeVarArgs && method.isVarArgs) continue
            if (searcher.isStatic != null && Modifier.isStatic(method.modifiers) != searcher.isStatic) continue
            if (searcher.visibility != null && !matchesVisibility(method.modifiers, searcher.visibility!!)) continue
            if (searcher.returnType != null && !ReflectTypeMatcher.isTypeCompatible(
                    searcher.returnType!!,
                    method.returnType,
                    searcher.sameNameTypeMatch
                )
            ) continue

            val requestedCount = args?.size ?: searcher.paramCount
            if (requestedCount != null && !parameterCountMatches(
                    method.parameterCount,
                    method.isVarArgs && searcher.includeVarArgs,
                    requestedCount
                )
            ) continue

            val conversion = if (args != null) {
                scoreExecutableParameters(
                    method.parameterTypes,
                    method.isVarArgs && searcher.includeVarArgs,
                    args,
                    searcher.sameNameTypeMatch
                ) ?: continue
            } else {
                0 to 0
            }
            result += ScoredMethod(
                method,
                ExecutableScore(
                    totalCost = conversion.first,
                    maxCost = conversion.second,
                    varArgsPenalty = if (method.isVarArgs) 1 else 0,
                    declaringDistance = distance[method.declaringClass] ?: Int.MAX_VALUE,
                    syntheticPenalty = if (method.isSynthetic) 1 else 0,
                    bridgePenalty = if (method.isBridge) 1 else 0
                )
            )
        }
    }
    return result
}

private val scoredMethodComparator = Comparator<ScoredMethod> { left, right ->
    val score = left.score.compareTo(right.score)
    if (score != 0) score else methodSignature(left.method).compareTo(methodSignature(right.method))
}

private fun ambiguousMethod(
    searcher: MethodSearcher,
    candidates: List<Method>
): AmbiguousMethodException {
    return AmbiguousMethodException(
        buildString {
            append("Ambiguous method match: ").append(searcher.describe())
            append(". Candidates: ")
            append(candidates.joinToString { methodSignature(it) })
        }
    )
}

private fun selectMostSpecific(methods: List<Method>): Method? {
    return methods.singleOrNull { candidate ->
        methods.all { other -> candidate === other || isMoreSpecific(candidate, other) }
    }
}

private fun isMoreSpecific(left: Method, right: Method): Boolean {
    val leftParams = left.parameterTypes
    val rightParams = right.parameterTypes
    if (leftParams.size != rightParams.size) return false

    var strictlyMoreSpecific = false
    for (index in leftParams.indices) {
        val leftType = ReflectTypeMatcher.boxed(leftParams[index])
        val rightType = ReflectTypeMatcher.boxed(rightParams[index])
        if (leftType == rightType) continue
        if (!rightType.isAssignableFrom(leftType)) return false
        strictlyMoreSpecific = true
    }
    return strictlyMoreSpecific
}

class FieldSearcher internal constructor(private val ownerClass: Class<*>) {

    var name: String? = null
    var type: Class<*>? = null
    var isStatic: Boolean? = null
    var visibility: Visibility? = null
    var inParent: Class<*>? = null

    var scope: SearchScope = SearchScope.HIERARCHY
    var includeSynthetic: Boolean = true
    var sameNameTypeMatch: Boolean = false
    var preferInstance: Boolean = true
    var index: Int = 0
    var requireUnique: Boolean = false

    val declared get() = SearchScope.DECLARED
    val superclasses get() = SearchScope.SUPERCLASSES
    val hierarchy get() = SearchScope.HIERARCHY

    internal val targetClass: Class<*> get() = inParent ?: ownerClass

    internal fun validate() {
        require(index >= 0) { "Field index cannot be negative: $index" }
        val start = inParent ?: return
        require(start.isAssignableFrom(ownerClass)) {
            "${start.name} is not a parent class or interface of ${ownerClass.name}"
        }
    }

    internal fun cacheKey(): ReflectCache.CacheKey {
        validate()
        return FieldCacheKey(
            owner = ownerClass,
            startClass = targetClass,
            name = name,
            type = type,
            isStatic = isStatic,
            visibility = visibility,
            scope = scope,
            includeSynthetic = includeSynthetic,
            sameNameTypeMatch = sameNameTypeMatch,
            preferInstance = preferInstance,
            index = index,
            requireUnique = requireUnique
        )
    }

    internal fun describe(): String = buildString {
        append("owner=").append(ownerClass.name)
        append(", start=").append(targetClass.name)
        append(", name=").append(name ?: "*")
        append(", type=").append(type?.name ?: "*")
        append(", static=").append(isStatic ?: "*")
        append(", visibility=").append(visibility ?: "*")
        append(", scope=").append(scope)
        append(", index=").append(index)
    }
}

fun Class<*>.findFieldOrNull(block: FieldSearcher.() -> Unit): Field? {
    val searcher = FieldSearcher(this).apply(block)
    return ReflectCache.getField(searcher.cacheKey()) {
        resolveField(searcher)
    }
}

fun Class<*>.findField(block: FieldSearcher.() -> Unit): Field {
    val searcher = FieldSearcher(this).apply(block)
    return ReflectCache.getField(searcher.cacheKey()) {
        resolveField(searcher)
    } ?: throw NoSuchFieldException("Field match failed: ${searcher.describe()}")
}

fun Class<*>.findFields(block: FieldSearcher.() -> Unit): List<Field> {
    val searcher = FieldSearcher(this).apply(block)
    searcher.validate()
    return collectFields(searcher).map { it.makeAccessible() }
}

private fun resolveField(searcher: FieldSearcher): Field? {
    val fields = collectFields(searcher)
    if (fields.isEmpty()) return null
    if (searcher.requireUnique && fields.size > 1) {
        throw AmbiguousFieldException(
            "Ambiguous field match: ${searcher.describe()}. Candidates: " +
                fields.joinToString { fieldSignature(it) }
        )
    }
    return fields.getOrNull(searcher.index)?.makeAccessible()
}

private fun collectFields(searcher: FieldSearcher): List<Field> {
    val result = ArrayList<Field>()
    for (clazz in collectSearchClasses(searcher.targetClass, searcher.scope)) {
        val declared = if (searcher.name != null) {
            listOfNotNull(runCatching { clazz.getDeclaredField(searcher.name!!) }.getOrNull())
        } else {
            clazz.declaredFields.toList()
        }

        val filtered = declared.filter { field ->
            (searcher.includeSynthetic || !field.isSynthetic) &&
                (searcher.type == null || ReflectTypeMatcher.isTypeCompatible(
                    searcher.type!!,
                    field.type,
                    searcher.sameNameTypeMatch
                )) &&
                (searcher.isStatic == null || Modifier.isStatic(field.modifiers) == searcher.isStatic) &&
                (searcher.visibility == null || matchesVisibility(field.modifiers, searcher.visibility!!))
        }

        val ordered = if (searcher.name != null) {
            filtered
        } else {
            val instanceFields = filtered.filterNot { Modifier.isStatic(it.modifiers) }
                .sortedBy(::fieldSignature)
            val staticFields = filtered.filter { Modifier.isStatic(it.modifiers) }
                .sortedBy(::fieldSignature)
            if (searcher.preferInstance) instanceFields + staticFields else staticFields + instanceFields
        }
        result += ordered
    }
    return result
}

internal fun Class<*>.resolveMethodForArguments(
    name: String,
    args: Array<out Any?>,
    returnType: Class<*>? = null,
    scope: SearchScope = SearchScope.HIERARCHY,
    isStatic: Boolean? = null
): Method? {
    val searcher = MethodSearcher(this).apply {
        this.name = name
        this.returnType = returnType
        this.paramCount = args.size
        this.scope = scope
        this.isStatic = isStatic
        this.argumentSpecs = args.map(::argumentSpecOf)
        this.requireUnique = false
        this.includeVarArgs = true
    }
    return ReflectCache.getMethod(searcher.cacheKey()) {
        resolveMethod(searcher)
    }
}

internal fun Class<*>.resolveConstructorForArguments(
    args: Array<out Any?>
): Constructor<*>? {
    val specs = args.map(::argumentSpecOf)
    val key = ConstructorCacheKey(this, specs, includeVarArgs = true)
    return ReflectCache.getConstructor(key) {
        val scored = declaredConstructors.mapNotNull { constructor ->
            val conversion = scoreExecutableParameters(
                constructor.parameterTypes,
                constructor.isVarArgs,
                specs,
                sameNameFallback = false
            ) ?: return@mapNotNull null
            ScoredConstructor(
                constructor,
                ExecutableScore(
                    totalCost = conversion.first,
                    maxCost = conversion.second,
                    varArgsPenalty = if (constructor.isVarArgs) 1 else 0,
                    declaringDistance = 0,
                    syntheticPenalty = if (constructor.isSynthetic) 1 else 0,
                    bridgePenalty = 0
                )
            )
        }.sortedWith { left, right ->
            val score = left.score.compareTo(right.score)
            if (score != 0) score else executableSignature(left.constructor)
                .compareTo(executableSignature(right.constructor))
        }

        if (scored.isEmpty()) return@getConstructor null
        val best = scored.first()
        val tied = scored.takeWhile { it.score == best.score }
        if (tied.size > 1) {
            throw AmbiguousMethodException(
                "Ambiguous constructor in ${this.name}: " +
                    tied.joinToString { executableSignature(it.constructor) }
            )
        }
        best.constructor.makeAccessible()
    }
}

internal fun resolveBestMethodForArguments(
    methods: Collection<Method>,
    args: Array<out Any?>
): Method? {
    val specs = args.map(::argumentSpecOf)
    val scored = methods.mapNotNull { method ->
        val conversion = scoreExecutableParameters(
            method.parameterTypes,
            method.isVarArgs,
            specs,
            sameNameFallback = false
        ) ?: return@mapNotNull null
        ScoredMethod(
            method,
            ExecutableScore(
                totalCost = conversion.first,
                maxCost = conversion.second,
                varArgsPenalty = if (method.isVarArgs) 1 else 0,
                declaringDistance = 0,
                syntheticPenalty = if (method.isSynthetic) 1 else 0,
                bridgePenalty = if (method.isBridge) 1 else 0
            )
        )
    }.sortedWith(scoredMethodComparator)

    if (scored.isEmpty()) return null
    val best = scored.first()
    val tied = scored.takeWhile { it.score == best.score }
    return when {
        tied.size == 1 -> best.method.makeAccessible()
        else -> selectMostSpecific(tied.map { it.method })?.makeAccessible()
            ?: throw AmbiguousMethodException(
                "Ambiguous methods: ${tied.joinToString { methodSignature(it.method) }}"
            )
    }
}

internal fun prepareInvocationArguments(
    executable: Executable,
    args: Array<out Any?>
): Array<Any?> {
    val normalized = args.map { if (it is TypedNull) null else it }.toTypedArray()
    if (!executable.isVarArgs) return normalized

    val parameterTypes = executable.parameterTypes
    val fixedCount = parameterTypes.size - 1

    if (normalized.size == parameterTypes.size) {
        val last = normalized.lastOrNull()
        val arrayType = parameterTypes.last()
        if (last == null || arrayType.isInstance(last)) return normalized
    }

    require(normalized.size >= fixedCount) {
        "Not enough arguments for ${executableSignature(executable)}"
    }

    val result = arrayOfNulls<Any?>(parameterTypes.size)
    for (index in 0 until fixedCount) result[index] = normalized[index]

    val componentType = parameterTypes.last().componentType
        ?: error("Varargs parameter is not an array type: ${parameterTypes.last().name}")
    val varArgCount = normalized.size - fixedCount
    val varArgArray = ReflectArray.newInstance(componentType, varArgCount)
    for (index in 0 until varArgCount) {
        ReflectArray.set(varArgArray, index, normalized[fixedCount + index])
    }
    result[fixedCount] = varArgArray
    return result
}

private fun argumentSpecOf(value: Any?): ArgumentSpec = when (value) {
    null -> ArgumentSpec.NullValue
    is TypedNull -> ArgumentSpec.TypedNullValue(value.type)
    else -> ArgumentSpec.Typed(value.javaClass)
}

private fun parameterCountMatches(
    declaredCount: Int,
    isVarArgs: Boolean,
    requestedCount: Int
): Boolean {
    return if (isVarArgs) requestedCount >= declaredCount - 1 else requestedCount == declaredCount
}

/** 返回 totalCost 与 maxCost。 */
private fun scoreExecutableParameters(
    parameterTypes: Array<Class<*>>,
    isVarArgs: Boolean,
    arguments: List<ArgumentSpec>,
    sameNameFallback: Boolean
): Pair<Int, Int>? {
    if (!isVarArgs) {
        if (parameterTypes.size != arguments.size) return null
        return scoreFixedParameters(parameterTypes, arguments, sameNameFallback)
    }

    val fixedCount = parameterTypes.size - 1
    if (arguments.size < fixedCount) return null

    var best: Pair<Int, Int>? = null

    if (arguments.size == parameterTypes.size) {
        scoreFixedParameters(parameterTypes, arguments, sameNameFallback)?.let {
            best = it
        }
    }

    val costs = ArrayList<Int>(arguments.size)
    for (index in 0 until fixedCount) {
        val cost = ReflectTypeMatcher.conversionCost(
            parameterTypes[index],
            arguments[index],
            sameNameFallback
        ) ?: return best
        costs += cost
    }

    val componentType = parameterTypes.last().componentType ?: return best
    for (index in fixedCount until arguments.size) {
        val cost = ReflectTypeMatcher.conversionCost(
            componentType,
            arguments[index],
            sameNameFallback
        ) ?: return best
        costs += cost
    }

    val expanded = (costs.sum() + 64) to (costs.maxOrNull() ?: 0)
    return when {
        best == null -> expanded
        expanded.first < best.first -> expanded
        else -> best
    }
}

private fun scoreFixedParameters(
    parameterTypes: Array<Class<*>>,
    arguments: List<ArgumentSpec>,
    sameNameFallback: Boolean
): Pair<Int, Int>? {
    var total = 0
    var max = 0
    for (index in parameterTypes.indices) {
        val cost = ReflectTypeMatcher.conversionCost(
            parameterTypes[index],
            arguments[index],
            sameNameFallback
        ) ?: return null
        total += cost
        if (cost > max) max = cost
    }
    return total to max
}

internal fun collectSearchClasses(
    startClass: Class<*>,
    scope: SearchScope
): List<Class<*>> {
    if (scope == SearchScope.DECLARED) return listOf(startClass)

    val classes = ArrayList<Class<*>>()
    var current: Class<*>? = startClass
    while (current != null) {
        classes += current
        current = current.superclass
    }

    if (scope == SearchScope.SUPERCLASSES) return classes

    val result = ArrayList<Class<*>>(classes)
    val visited = HashSet<Class<*>>(classes)
    val queue = ArrayDeque<Class<*>>()
    classes.forEach { clazz -> clazz.interfaces.forEach(queue::add) }

    while (queue.isNotEmpty()) {
        val iface = queue.removeFirst()
        if (!visited.add(iface)) continue
        result += iface
        iface.interfaces.forEach(queue::add)
    }
    return result
}

private fun matchesVisibility(modifiers: Int, visibility: Visibility): Boolean = when (visibility) {
    Visibility.PUBLIC -> Modifier.isPublic(modifiers)
    Visibility.PROTECTED -> Modifier.isProtected(modifiers)
    Visibility.PRIVATE -> Modifier.isPrivate(modifiers)
    Visibility.PACKAGE -> !Modifier.isPublic(modifiers) &&
        !Modifier.isProtected(modifiers) &&
        !Modifier.isPrivate(modifiers)
}

@Suppress("DEPRECATION")
internal fun <T : java.lang.reflect.AccessibleObject> T.makeAccessible(): T {
    if (!isAccessible) runCatching { isAccessible = true }
    return this
}

private fun methodSignature(method: Method): String = buildString {
    append(method.declaringClass.name).append('#').append(method.name)
    append(method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name })
    append(':').append(method.returnType.name)
}

private fun fieldSignature(field: Field): String =
    "${field.declaringClass.name}#${field.name}:${field.type.name}"

private fun executableSignature(executable: Executable): String = buildString {
    append(executable.declaringClass.name)
    if (executable is Method) append('#').append(executable.name)
    append(executable.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name })
}
