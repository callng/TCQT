package com.owo233.tcqt.utils.reflect

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque

enum class Visibility {
    PUBLIC,
    PROTECTED,
    PRIVATE,
    PACKAGE
}

/**
 * 类型匹配策略。
 */
enum class TypeMatchMode {

    /**
     * 支持继承、接口以及基本类型装箱匹配。
     *
     * 例如：
     * Number <- Integer
     * CharSequence <- String
     * int <-> Integer
     */
    COMPATIBLE,

    /**
     * 严格遵循 Class.isAssignableFrom，不进行基本类型装箱。
     *
     * 例如：
     * Number <- Integer
     * int 与 Integer 不匹配
     */
    ASSIGNABLE,

    /**
     * JVM 类型必须完全相同。
     *
     * int 与 Integer 不匹配。
     */
    EXACT,

    /**
     * 装箱后的类型必须完全相同。
     *
     * int 与 Integer 匹配，
     * Number 与 Integer 不匹配。
     */
    BOXED_EXACT
}

/**
 * 成员搜索范围。
 */
enum class SearchScope {

    /**
     * 只搜索当前类声明的成员。
     */
    DECLARED_ONLY,

    /**
     * 搜索当前类及其所有父类。
     */
    SUPERCLASSES,

    /**
     * 搜索当前类、父类以及接口树。
     */
    HIERARCHY
}

/**
 * 多个成员同时匹配时的处理方式。
 */
enum class AmbiguityStrategy {

    /**
     * 参数类型存在时选择距离最近的方法。
     * 如果仍有多个同分候选，则抛出异常。
     */
    BEST_MATCH,

    /**
     * 直接取稳定排序后的第一个候选。
     */
    FIRST,

    /**
     * 只要存在多个候选就抛出异常。
     */
    FAIL
}

class AmbiguousMemberException(
    message: String
) : ReflectiveOperationException(message)

class MethodSearcher internal constructor(
    private val ownerClass: Class<*>
) {

    var name: String? = null
    var returnType: Class<*>? = null
    var returnTypeMatch: TypeMatchMode = TypeMatchMode.COMPATIBLE
    var paramTypes: Array<out Class<*>?>? = null
        set(value) {
            field = value?.copyOf()
        }
    var paramTypeMatch: TypeMatchMode = TypeMatchMode.COMPATIBLE
    var paramCount: Int? = null
    var isStatic: Boolean? = null
    var isFinal: Boolean? = null
    var isAbstract: Boolean? = null
    var isNative: Boolean? = null
    var isSynchronized: Boolean? = null
    var visibility: Visibility? = null

    /**
     * 默认排除编译器生成的方法。
     */
    var includeSynthetic: Boolean = false
    var includeBridge: Boolean = false

    var scope: SearchScope = SearchScope.DECLARED_ONLY
    var ambiguityStrategy: AmbiguityStrategy = AmbiguityStrategy.BEST_MATCH

    val private get() = Visibility.PRIVATE
    val public get() = Visibility.PUBLIC
    val protected get() = Visibility.PROTECTED
    val pkg get() = Visibility.PACKAGE

    val compatible get() = TypeMatchMode.COMPATIBLE
    val assignable get() = TypeMatchMode.ASSIGNABLE
    val exact get() = TypeMatchMode.EXACT
    val boxedExact get() = TypeMatchMode.BOXED_EXACT

    val declaredOnly get() = SearchScope.DECLARED_ONLY
    val superclasses get() = SearchScope.SUPERCLASSES
    val hierarchy get() = SearchScope.HIERARCHY

    val bestMatch get() = AmbiguityStrategy.BEST_MATCH
    val first get() = AmbiguityStrategy.FIRST
    val failOnAmbiguous get() = AmbiguityStrategy.FAIL

    val void: Class<*> get() = Void.TYPE
    val boolean: Class<*> get() = java.lang.Boolean.TYPE
    val byte: Class<*> get() = java.lang.Byte.TYPE
    val short: Class<*> get() = java.lang.Short.TYPE
    val int: Class<*> get() = Integer.TYPE
    val long: Class<*> get() = java.lang.Long.TYPE
    val float: Class<*> get() = java.lang.Float.TYPE
    val double: Class<*> get() = java.lang.Double.TYPE
    val char: Class<*> get() = Character.TYPE

    val intent: Class<*> get() = android.content.Intent::class.java
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
        paramTypes = types
    }

    internal fun validate() {
        require(paramCount == null || paramCount!! >= 0) {
            "paramCount must be greater than or equal to 0"
        }

        val types = paramTypes
        require(paramCount == null || types == null || paramCount == types.size) {
            "paramCount=$paramCount conflicts with paramTypes.size=${types?.size}"
        }
    }

    internal fun uniqueKey(): String {
        validate()

        return buildString {
            append(classIdentity(ownerClass))
            append("#M")
            append("|name=")
            append(name ?: "*")
            append("|return=")
            append(typeIdentity(returnType))
            append("|returnMatch=")
            append(returnTypeMatch)
            append("|params=")
            append(
                paramTypes?.joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { typeIdentity(it) } ?: "*"
            )
            append("|paramMatch=")
            append(paramTypeMatch)
            append("|count=")
            append(paramCount ?: "*")
            append("|static=")
            append(isStatic ?: "*")
            append("|final=")
            append(isFinal ?: "*")
            append("|abstract=")
            append(isAbstract ?: "*")
            append("|native=")
            append(isNative ?: "*")
            append("|synchronized=")
            append(isSynchronized ?: "*")
            append("|visibility=")
            append(visibility ?: "*")
            append("|synthetic=")
            append(includeSynthetic)
            append("|bridge=")
            append(includeBridge)
            append("|scope=")
            append(scope)
            append("|ambiguity=")
            append(ambiguityStrategy)
        }
    }

    internal fun describe(): String {
        return buildString {
            append("owner=")
            append(ownerClass.name)
            append(", name=")
            append(name ?: "*")
            append(", returnType=")
            append(returnType?.typeName ?: "*")
            append(" [")
            append(returnTypeMatch)
            append(']')
            append(", parameterTypes=")
            append(
                paramTypes?.joinToString(
                    prefix = "(",
                    postfix = ")"
                ) { it?.typeName ?: "*" } ?: "*"
            )
            append(" [")
            append(paramTypeMatch)
            append(']')
            append(", scope=")
            append(scope)
        }
    }

    internal fun match(method: Method): Boolean {
        if (!includeSynthetic && method.isSynthetic) return false
        if (!includeBridge && method.isBridge) return false

        if (name != null && method.name != name) return false

        if (
            returnType != null &&
            !matchReturnType(
                expected = returnType!!,
                actual = method.returnType,
                mode = returnTypeMatch
            )
        ) {
            return false
        }

        if (paramCount != null && method.parameterCount != paramCount) {
            return false
        }

        paramTypes?.let { searchTypes ->
            if (method.parameterCount != searchTypes.size) return false

            val actualTypes = method.parameterTypes

            searchTypes.forEachIndexed { index, expectedType ->
                if (expectedType == null) return@forEachIndexed

                if (
                    !matchParameterType(
                        expected = expectedType,
                        actual = actualTypes[index],
                        mode = paramTypeMatch
                    )
                ) {
                    return false
                }
            }
        }

        val modifiers = method.modifiers

        if (isStatic != null && Modifier.isStatic(modifiers) != isStatic) return false
        if (isFinal != null && Modifier.isFinal(modifiers) != isFinal) return false
        if (isAbstract != null && Modifier.isAbstract(modifiers) != isAbstract) return false
        if (isNative != null && Modifier.isNative(modifiers) != isNative) return false
        if (
            isSynchronized != null &&
            Modifier.isSynchronized(modifiers) != isSynchronized
        ) {
            return false
        }

        if (
            visibility != null &&
            !matchVisibility(modifiers, visibility!!)
        ) {
            return false
        }

        return true
    }
}

fun Class<*>.findMethodOrNull(
    block: MethodSearcher.() -> Unit
): Method? {
    val searcher = MethodSearcher(this).apply(block)
    return findMethodOrNull(searcher)
}

fun Class<*>.findMethod(
    block: MethodSearcher.() -> Unit
): Method {
    val searcher = MethodSearcher(this).apply(block)

    return findMethodOrNull(searcher)
        ?: throw NoSuchMethodException(
            "Method match failed. ${searcher.describe()}"
        )
}

fun Class<*>.findMethods(
    block: MethodSearcher.() -> Unit
): List<Method> {
    val searcher = MethodSearcher(this).apply(block)
    searcher.validate()

    return collectMethods(searcher.scope)
        .filter(searcher::match)
        .sortedWith(methodStableComparator)
        .onEach(Method::makeAccessible)
        .toList()
}

private fun Class<*>.findMethodOrNull(
    searcher: MethodSearcher
): Method? {
    searcher.validate()

    return ReflectCache.getMethod(searcher.uniqueKey()) {
        val candidates = collectMethods(searcher.scope)
            .filter(searcher::match)
            .sortedWith(methodStableComparator)
            .toList()

        selectMethod(candidates, searcher)?.makeAccessible()
    }
}

private fun selectMethod(
    candidates: List<Method>,
    searcher: MethodSearcher
): Method? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    return when (searcher.ambiguityStrategy) {
        AmbiguityStrategy.FIRST -> {
            candidates.first()
        }

        AmbiguityStrategy.FAIL -> {
            throw ambiguousMethodException(candidates, searcher)
        }

        AmbiguityStrategy.BEST_MATCH -> {
            val searchTypes = searcher.paramTypes
                ?: throw ambiguousMethodException(candidates, searcher)

            val scored = candidates.map { method ->
                method to calculateMethodScore(
                    method = method,
                    searchTypes = searchTypes
                )
            }

            val bestScore = scored.minOf { it.second }
            val bestMethods = scored
                .filter { it.second == bestScore }
                .map { it.first }

            if (bestMethods.size != 1) {
                throw ambiguousMethodException(bestMethods, searcher)
            }

            bestMethods.first()
        }
    }
}

private fun ambiguousMethodException(
    candidates: List<Method>,
    searcher: MethodSearcher
): AmbiguousMemberException {
    return AmbiguousMemberException(
        buildString {
            appendLine("Multiple methods matched. ${searcher.describe()}")
            candidates.forEach { method ->
                append("  ")
                appendLine(method.toGenericString())
            }
        }.trimEnd()
    )
}

class FieldSearcher internal constructor(
    private val ownerClass: Class<*>
) {

    var name: String? = null
    var type: Class<*>? = null
    var typeMatch: TypeMatchMode = TypeMatchMode.COMPATIBLE
    var isStatic: Boolean? = null
    var isFinal: Boolean? = null
    var isVolatile: Boolean? = null
    var isTransient: Boolean? = null
    var visibility: Visibility? = null
    var includeSynthetic: Boolean = false

    /**
     * 指定字段实际声明类。
     *
     * 它替代旧版 inParent 的含糊语义。
     */
    var declaredIn: Class<*>? = null

    /**
     * 兼容旧调用方式。
     */
    @Deprecated(
        message = "Use declaredIn instead",
        replaceWith = ReplaceWith("declaredIn")
    )
    var inParent: Class<*>?
        get() = declaredIn
        set(value) {
            declaredIn = value
        }

    var scope: SearchScope = SearchScope.DECLARED_ONLY
    var ambiguityStrategy: AmbiguityStrategy = AmbiguityStrategy.FAIL

    val private get() = Visibility.PRIVATE
    val public get() = Visibility.PUBLIC
    val protected get() = Visibility.PROTECTED
    val pkg get() = Visibility.PACKAGE

    val compatible get() = TypeMatchMode.COMPATIBLE
    val assignable get() = TypeMatchMode.ASSIGNABLE
    val exact get() = TypeMatchMode.EXACT
    val boxedExact get() = TypeMatchMode.BOXED_EXACT

    val declaredOnly get() = SearchScope.DECLARED_ONLY
    val superclasses get() = SearchScope.SUPERCLASSES
    val hierarchy get() = SearchScope.HIERARCHY

    val first get() = AmbiguityStrategy.FIRST
    val failOnAmbiguous get() = AmbiguityStrategy.FAIL

    internal val targetClass: Class<*>
        get() = declaredIn ?: ownerClass

    internal fun validate() {
        declaredIn?.let { declaringClass ->
            require(declaringClass.isAssignableFrom(ownerClass)) {
                "${declaringClass.name} is not a parent or interface of ${ownerClass.name}"
            }
        }
    }

    internal fun uniqueKey(): String {
        validate()

        return buildString {
            append(classIdentity(targetClass))
            append("#F")
            append("|name=")
            append(name ?: "*")
            append("|type=")
            append(typeIdentity(type))
            append("|typeMatch=")
            append(typeMatch)
            append("|static=")
            append(isStatic ?: "*")
            append("|final=")
            append(isFinal ?: "*")
            append("|volatile=")
            append(isVolatile ?: "*")
            append("|transient=")
            append(isTransient ?: "*")
            append("|visibility=")
            append(visibility ?: "*")
            append("|synthetic=")
            append(includeSynthetic)
            append("|scope=")
            append(scope)
            append("|ambiguity=")
            append(ambiguityStrategy)
        }
    }

    internal fun describe(): String {
        return buildString {
            append("owner=")
            append(targetClass.name)
            append(", name=")
            append(name ?: "*")
            append(", type=")
            append(type?.typeName ?: "*")
            append(" [")
            append(typeMatch)
            append(']')
            append(", scope=")
            append(scope)
        }
    }

    internal fun match(field: Field): Boolean {
        if (!includeSynthetic && field.isSynthetic) return false
        if (name != null && field.name != name) return false

        if (
            type != null &&
            !matchFieldType(
                expected = type!!,
                actual = field.type,
                mode = typeMatch
            )
        ) {
            return false
        }

        val modifiers = field.modifiers

        if (isStatic != null && Modifier.isStatic(modifiers) != isStatic) return false
        if (isFinal != null && Modifier.isFinal(modifiers) != isFinal) return false
        if (isVolatile != null && Modifier.isVolatile(modifiers) != isVolatile) return false
        if (isTransient != null && Modifier.isTransient(modifiers) != isTransient) return false

        if (
            visibility != null &&
            !matchVisibility(modifiers, visibility!!)
        ) {
            return false
        }

        return true
    }
}

fun Class<*>.findFieldOrNull(
    block: FieldSearcher.() -> Unit
): Field? {
    val searcher = FieldSearcher(this).apply(block)
    return findFieldOrNull(searcher)
}

fun Class<*>.findField(
    block: FieldSearcher.() -> Unit
): Field {
    val searcher = FieldSearcher(this).apply(block)

    return findFieldOrNull(searcher)
        ?: throw NoSuchFieldException(
            "Field match failed. ${searcher.describe()}"
        )
}

fun Class<*>.findFields(
    block: FieldSearcher.() -> Unit
): List<Field> {
    val searcher = FieldSearcher(this).apply(block)
    searcher.validate()

    return searcher.targetClass
        .collectFields(searcher.scope)
        .filter(searcher::match)
        .sortedWith(fieldStableComparator)
        .onEach(Field::makeAccessible)
        .toList()
}

private fun Class<*>.findFieldOrNull(
    searcher: FieldSearcher
): Field? {
    searcher.validate()

    return ReflectCache.getField(searcher.uniqueKey()) {
        val candidates = searcher.targetClass
            .collectFields(searcher.scope)
            .filter(searcher::match)
            .sortedWith(fieldStableComparator)
            .toList()

        when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first().makeAccessible()
            searcher.ambiguityStrategy == AmbiguityStrategy.FIRST ->
                candidates.first().makeAccessible()
            else -> throw AmbiguousMemberException(
                buildString {
                    appendLine("Multiple fields matched. ${searcher.describe()}")
                    candidates.forEach { field ->
                        append("  ")
                        appendLine(field.toGenericString())
                    }
                }.trimEnd()
            )
        }
    }
}

private fun matchReturnType(
    expected: Class<*>,
    actual: Class<*>,
    mode: TypeMatchMode
): Boolean {
    return matchType(
        receiver = expected,
        provided = actual,
        mode = mode
    )
}

private fun matchParameterType(
    expected: Class<*>,
    actual: Class<*>,
    mode: TypeMatchMode
): Boolean {
    return matchType(
        receiver = actual,
        provided = expected,
        mode = mode
    )
}

private fun matchFieldType(
    expected: Class<*>,
    actual: Class<*>,
    mode: TypeMatchMode
): Boolean {
    return matchType(
        receiver = expected,
        provided = actual,
        mode = mode
    )
}

private fun matchType(
    receiver: Class<*>,
    provided: Class<*>,
    mode: TypeMatchMode
): Boolean {
    return when (mode) {
        TypeMatchMode.COMPATIBLE -> {
            receiver.boxed().isAssignableFrom(provided.boxed())
        }

        TypeMatchMode.ASSIGNABLE -> {
            receiver.isAssignableFrom(provided)
        }

        TypeMatchMode.EXACT -> {
            receiver == provided
        }

        TypeMatchMode.BOXED_EXACT -> {
            receiver.boxed() == provided.boxed()
        }
    }
}

private fun matchVisibility(
    modifiers: Int,
    visibility: Visibility
): Boolean {
    return when (visibility) {
        Visibility.PUBLIC -> Modifier.isPublic(modifiers)
        Visibility.PROTECTED -> Modifier.isProtected(modifiers)
        Visibility.PRIVATE -> Modifier.isPrivate(modifiers)
        Visibility.PACKAGE ->
            !Modifier.isPublic(modifiers) &&
                    !Modifier.isProtected(modifiers) &&
                    !Modifier.isPrivate(modifiers)
    }
}

private fun calculateMethodScore(
    method: Method,
    searchTypes: Array<out Class<*>?>
): Int {
    val methodTypes = method.parameterTypes
    var score = 0

    for (index in searchTypes.indices) {
        val providedType = searchTypes[index] ?: continue
        val receiverType = methodTypes[index]

        score += calculateTypeDistance(
            source = providedType,
            target = receiverType
        )
    }

    return score
}

/**
 * 计算 source 转换为 target 所需的继承或接口距离。
 *
 * 使用广度优先搜索，同时遍历父类和接口。
 */
private fun calculateTypeDistance(
    source: Class<*>,
    target: Class<*>
): Int {
    val from = source.boxed()
    val to = target.boxed()

    if (from == to) return 0
    if (!to.isAssignableFrom(from)) return UNREACHABLE_TYPE_DISTANCE

    val queue = ArrayDeque<TypeNode>()
    val visited = HashSet<Class<*>>()

    queue.addLast(TypeNode(from, 0))
    visited.add(from)

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()

        if (node.type == to) {
            return node.distance
        }

        node.type.superclass?.let { superclass ->
            if (visited.add(superclass)) {
                queue.addLast(
                    TypeNode(
                        type = superclass,
                        distance = node.distance + 1
                    )
                )
            }
        }

        node.type.interfaces.forEach { interfaceType ->
            if (visited.add(interfaceType)) {
                queue.addLast(
                    TypeNode(
                        type = interfaceType,
                        distance = node.distance + 1
                    )
                )
            }
        }
    }

    return UNREACHABLE_TYPE_DISTANCE
}

private data class TypeNode(
    val type: Class<*>,
    val distance: Int
)

private const val UNREACHABLE_TYPE_DISTANCE = 1_000_000

private fun Class<*>.collectMethods(
    scope: SearchScope
): Sequence<Method> {
    return when (scope) {
        SearchScope.DECLARED_ONLY -> {
            declaredMethods.asSequence()
        }

        SearchScope.SUPERCLASSES -> {
            classHierarchy()
                .flatMap { it.declaredMethods.asSequence() }
        }

        SearchScope.HIERARCHY -> {
            sequence {
                val visited = HashSet<Class<*>>()
                val queue = ArrayDeque<Class<*>>()
                queue.addLast(this@collectMethods)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (!visited.add(current)) continue

                    yieldAll(current.declaredMethods.asSequence())

                    current.superclass?.let(queue::addLast)
                    current.interfaces.forEach(queue::addLast)
                }
            }
        }
    }
}

private fun Class<*>.collectFields(
    scope: SearchScope
): Sequence<Field> {
    return when (scope) {
        SearchScope.DECLARED_ONLY -> {
            declaredFields.asSequence()
        }

        SearchScope.SUPERCLASSES -> {
            classHierarchy()
                .flatMap { it.declaredFields.asSequence() }
        }

        SearchScope.HIERARCHY -> {
            sequence {
                val visited = HashSet<Class<*>>()
                val queue = ArrayDeque<Class<*>>()
                queue.addLast(this@collectFields)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (!visited.add(current)) continue

                    yieldAll(current.declaredFields.asSequence())

                    current.superclass?.let(queue::addLast)
                    current.interfaces.forEach(queue::addLast)
                }
            }
        }
    }
}

private fun Class<*>.classHierarchy(): Sequence<Class<*>> {
    return generateSequence(this) { current ->
        current.superclass
    }
}

private val methodStableComparator =
    compareBy<Method>(
        { it.declaringClass.name },
        { it.name },
        { it.parameterTypes.joinToString(",") { type -> type.name } },
        { it.returnType.name },
        { it.modifiers }
    )

private val fieldStableComparator =
    compareBy<Field>(
        { it.declaringClass.name },
        { it.name },
        { it.type.name },
        { it.modifiers }
    )

private fun Class<*>.boxed(): Class<*> {
    return if (isPrimitive) {
        this.kotlin.javaObjectType
    } else {
        this
    }
}

private fun classIdentity(clazz: Class<*>): String {
    return buildString {
        append(clazz.name)
        append('@')
        append(System.identityHashCode(clazz))
        append(":loader@")
        append(System.identityHashCode(clazz.classLoader))
    }
}

private fun typeIdentity(clazz: Class<*>?): String {
    return clazz?.let(::classIdentity) ?: "*"
}

private fun Method.makeAccessible(): Method {
    try {
        isAccessible = true
        return this
    } catch (throwable: Throwable) {
        throw IllegalStateException(
            "Unable to make method accessible: ${toGenericString()}",
            throwable
        )
    }
}

private fun Field.makeAccessible(): Field {
    try {
        isAccessible = true
        return this
    } catch (throwable: Throwable) {
        throw IllegalStateException(
            "Unable to make field accessible: ${toGenericString()}",
            throwable
        )
    }
}
