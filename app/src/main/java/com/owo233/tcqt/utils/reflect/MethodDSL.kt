package com.owo233.tcqt.utils.reflect

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class MethodSearcher internal constructor(
    private val clazz: Class<*>
) {

    enum class Visibility { PUBLIC, PROTECTED, PACKAGE, PRIVATE }

    var name: String? = null
    var returnType: Class<*>? = null
    var paramTypes: Array<out Class<*>?>? = null
    var paramCount: Int? = null
    var isStatic: Boolean? = null
    var visibility: Visibility? = null
    var recursive: Boolean = false
    var includeObjectMethods: Boolean = false
    var matchTypeByNameFallback: Boolean = true

    /* ---------- visibility shortcuts ---------- */
    val public get() = Visibility.PUBLIC
    val protected get() = Visibility.PROTECTED
    val private get() = Visibility.PRIVATE
    val pkg get() = Visibility.PACKAGE

    /* ---------- primitive shortcuts ---------- */
    val void: Class<*>    get() = Void.TYPE
    val boolean get() = Boolean::class.javaPrimitiveType
    val byte    get() = Byte::class.javaPrimitiveType
    val short   get() = Short::class.javaPrimitiveType
    val int     get() = Int::class.javaPrimitiveType
    val long    get() = Long::class.javaPrimitiveType
    val float   get() = Float::class.javaPrimitiveType
    val double  get() = Double::class.javaPrimitiveType
    val char    get() = Char::class.javaPrimitiveType

    /* ---------- common object shortcuts ---------- */
    val string: Class<*> get() = String::class.java
    val obj: Class<*> get() = Any::class.java
    val map: Class<*> get() = Map::class.java
    val hashMap: Class<*> get() = HashMap::class.java
    val list: Class<*> get() = List::class.java
    val arrayList: Class<*> get() = ArrayList::class.java
    val set: Class<*> get() = Set::class.java

    val activity: Class<*> get() = android.app.Activity::class.java
    val context: Class<*> get() = android.content.Context::class.java
    val bundle: Class<*> get() = android.os.Bundle::class.java
    val intent: Class<*> get() = android.content.Intent::class.java
    val view: Class<*> get() = android.view.View::class.java

    /* ---------- array shortcuts ---------- */
    val byteArr: Class<*> get() = ByteArray::class.java
    val intArr: Class<*> get() = IntArray::class.java
    val longArr: Class<*> get() = LongArray::class.java
    val floatArr: Class<*> get() = FloatArray::class.java
    val stringArr: Class<*> get() = Array<String>::class.java
    val objArr: Class<*> get() = Array<Any>::class.java

    fun paramTypes(vararg types: Class<*>?) {
        this.paramTypes = types
        this.paramCount = types.size
    }

    internal fun find(): Method {
        val key = generateCacheKey()
        if (key != null) {
            methodCache[key]?.let { return ensureAccessible(it) }
        }

        val candidates = collectCandidates()
        if (candidates.isEmpty()) {
            throw NoSuchMethodException(buildErrorMsg())
        }

        val best = if (paramTypes == null) {
            candidates.first()
        } else {
            candidates.maxByOrNull { scoreMatch(it) } ?: candidates.first()
        }

        ensureAccessible(best)
        if (key != null) {
            methodCache[key] = best
        }
        return best
    }

    private fun collectCandidates(): List<Method> {
        val out = ArrayList<Method>()

        fun scan(c: Class<*>) {
            for (m in c.declaredMethods) {
                if (!includeObjectMethods && c == Any::class.java) continue
                if (isPotentialMatch(m)) out.add(m)
            }
        }

        scan(clazz)

        if (recursive) {
            var c: Class<*>? = clazz.superclass
            while (c != null && (includeObjectMethods || c != Any::class.java)) {
                scan(c)
                c = c.superclass
            }
            if (includeObjectMethods) scan(Any::class.java)
        }

        return out
    }

    private fun isPotentialMatch(m: Method): Boolean {
        if (name != null && m.name != name) return false
        if (paramCount != null && m.parameterCount != paramCount) return false
        if (isStatic != null && Modifier.isStatic(m.modifiers) != isStatic) return false
        if (visibility != null && !matchVisibility(m.modifiers, visibility!!)) return false
        if (returnType != null && !checkTypeCompatible(returnType!!, m.returnType)) return false

        val wanted = paramTypes ?: return true
        val actual = m.parameterTypes
        if (actual.size != wanted.size) return false

        for (i in wanted.indices) {
            val w = wanted[i] ?: continue
            if (!checkTypeCompatible(w, actual[i])) return false
        }

        return true
    }

    private fun scoreMatch(m: Method): Int {
        val wanted = paramTypes ?: return 0
        val actual = m.parameterTypes
        var score = 0

        for (i in wanted.indices) {
            val w = wanted[i] ?: continue
            val a = actual[i]

            when {
                w == a -> score += 100
                toWrapper(w) == toWrapper(a) -> score += 70
                checkTypeCompatible(w, a) -> score += 20
                matchTypeByNameFallback && w.name == a.name -> score += 5
            }
        }

        return score
    }

    private fun checkTypeCompatible(expected: Class<*>, actual: Class<*>): Boolean {
        if (expected == actual) return true
        if (expected.isAssignableFrom(actual)) return true

        // 自动拆装箱兼容
        if (toWrapper(expected) == toWrapper(actual)) return true

        // 类名兜底
        return matchTypeByNameFallback && expected.name == actual.name
    }

    private fun matchVisibility(mod: Int, v: Visibility): Boolean = when (v) {
        Visibility.PUBLIC    -> Modifier.isPublic(mod)
        Visibility.PROTECTED -> Modifier.isProtected(mod)
        Visibility.PRIVATE   -> Modifier.isPrivate(mod)
        Visibility.PACKAGE   -> !Modifier.isPublic(mod) && !Modifier.isProtected(mod) && !Modifier.isPrivate(mod)
    }

    private fun ensureAccessible(m: Method): Method = m.apply {
        if (!isAccessible) {
            kotlin.runCatching { isAccessible = true }
        }
    }

    private fun generateCacheKey(): String? {
        if (name == null && returnType == null && paramTypes == null &&
            paramCount == null && isStatic == null && visibility == null
        ) return null

        return buildString {
            append(clazz.name).append('@').append(clazz.classLoader?.hashCode() ?: 0).append('|')
            append(name ?: "*").append('|')
            append(returnType?.name ?: "*").append('|')
            append(paramCount ?: "*").append('|')
            if (paramTypes != null) {
                append("p[")
                paramTypes!!.joinTo(this, ",") { it?.name ?: "?" }
                append("]")
            } else {
                append("p[*]")
            }
            append('|')
            append(isStatic ?: "*").append('|')
            append(visibility ?: "*").append('|')
            append(recursive).append('|')
            append(includeObjectMethods).append('|')
            append(matchTypeByNameFallback)
        }
    }

    private fun buildErrorMsg(): String = buildString {
        append("No matching method found in ${clazz.name} (loader=${clazz.classLoader?.hashCode() ?: "null"})\n")
        append("  • name      = ${name ?: "<any>"}\n")
        append("  • return    = ${returnType?.name ?: "<any>"}\n")
        append("  • params    = ")
        if (paramTypes != null) {
            append(paramTypes!!.joinToString(", ") { it?.name ?: "?" })
        } else {
            append("count=${paramCount ?: "<any>"}")
        }
        append("\n")
        append("  • static    = ${isStatic ?: "<any>"}\n")
        append("  • visibility= ${visibility ?: "<any>"}\n")
        append("  • recursive = $recursive")
    }

    private companion object {
        private val primitiveToWrapper = mapOf(
            Boolean::class.javaPrimitiveType   to Boolean::class.java,
            Byte::class.javaPrimitiveType      to Byte::class.java,
            Char::class.javaPrimitiveType      to Char::class.java,
            Short::class.javaPrimitiveType     to Short::class.java,
            Int::class.javaPrimitiveType       to Int::class.java,
            Long::class.javaPrimitiveType      to Long::class.java,
            Float::class.javaPrimitiveType     to Float::class.java,
            Double::class.javaPrimitiveType    to Double::class.java,
            Void.TYPE                          to Void::class.java
        )

        fun toWrapper(clazz: Class<*>): Class<*> = primitiveToWrapper[clazz] ?: clazz
    }
}

private val methodCache = ConcurrentHashMap<String, Method>()

fun Class<*>.findMethod(block: MethodSearcher.() -> Unit): Method =
    MethodSearcher(this).apply(block).find()

fun Class<*>.findMethodOrNull(block: MethodSearcher.() -> Unit): Method? =
    kotlin.runCatching { findMethod(block) }.getOrNull()

fun clearMethodCache() {
    methodCache.clear()
}
