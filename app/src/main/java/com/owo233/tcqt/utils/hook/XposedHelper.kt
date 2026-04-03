package com.owo233.tcqt.utils.hook

import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

typealias MethodHookParam = com.owo233.tcqt.loader.api.HookParam

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
