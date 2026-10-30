package com.owo233.tcqt.ui.parasitic

import androidx.annotation.Keep
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap

// 名字保持稳定：热重载的新 ClassLoader 需要识别旧 ClassLoader 中的包装器。
@Keep
interface TCQTProxyMarker

internal object ProxyChain {

    fun unwrap(value: Any?, isWrapper: (Any) -> Boolean): Any? =
        unwrap(value, isWrapper, Collections.newSetFromMap(IdentityHashMap()))

    private fun unwrap(value: Any?, isWrapper: (Any) -> Boolean, visited: MutableSet<Any>): Any? {
        if (value == null || !visited.add(value)) return value
        if (owns(value.javaClass)) {
            val base = value.javaClass.getDeclaredField("base").apply { isAccessible = true }.get(value)
            return unwrap(base, isWrapper, visited)
        }
        if (Proxy.isProxyClass(value.javaClass)) {
            val handler = Proxy.getInvocationHandler(value)
            if (owns(handler.javaClass)) return unwrap(handler, isWrapper, visited)
            unwrap(handler, isWrapper, visited)
            return value
        }
        if (!isWrapper(value) && value !is InvocationHandler) return value

        // 仅遍历实际的代理委托链，绝不深入 Context/线程/集合等任意宿主对象图。
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            if (type.name.startsWith("android.") || type.name.startsWith("java.")) break
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                runCatching {
                    field.isAccessible = true
                    val delegate = field.get(value) ?: return@runCatching
                    if (!isWrapper(delegate) && !Proxy.isProxyClass(delegate.javaClass)) return@runCatching
                    val cleaned = unwrap(delegate, isWrapper, visited)
                    if (cleaned !== delegate) field.set(value, cleaned)
                }.getOrThrow()
            }
            type = type.superclass
        }
        return value
    }

    private fun owns(clazz: Class<*>): Boolean = generateSequence(clazz) { it.superclass }
        .any { type -> type.interfaces.any { it.name == TCQTProxyMarker::class.java.name } }
}
