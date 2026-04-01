package com.owo233.tcqt.utils.hook

import com.owo233.tcqt.hooks.base.load
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

typealias MethodHookParam = com.owo233.tcqt.loader.api.HookParam

object FuzzyClassKit {
    private val dic = arrayOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
        "u", "v", "w", "x", "y", "z"
    )

    fun findMethodByClassPrefix(
        prefix: String,
        isSubClass: Boolean = false,
        check: (Class<*>, Method) -> Boolean
    ): Method? {
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
                ?: dic.firstNotNullOfOrNull { innerClass ->
                    val innerName = "$mainName$$innerClass"
                    val innerClz = load(innerName) ?: return@firstNotNullOfOrNull null
                    innerClz.declaredMethods.firstOrNull { check(innerClz, it) }
                }
        }
    }

    fun findClassByMethod(
        prefix: String,
        isSubClass: Boolean = false,
        check: (Class<*>, Method) -> Boolean
    ): Class<*>? {
        dic.forEach { name ->
            val clz = load("$prefix${if (isSubClass) "$" else "."}$name")
            clz?.declaredMethods?.forEach {
                if (check(clz, it)) return clz
            }
        }
        return null
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
