package com.owo233.tcqt.ui.parasitic

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProxyChainTest {

    interface Callback { fun invoke(): Boolean }
    class Original : Callback { override fun invoke() = true }
    class Owned(val base: Callback?) : Callback, TCQTProxyMarker {
        override fun invoke() = base?.invoke() ?: false
    }
    class Foreign(var delegate: Callback, val unrelated: Holder? = null) : Callback {
        override fun invoke() = delegate.invoke()
    }
    class Holder(var callback: Callback)
    class OwnedInvocation(val base: Callback) : InvocationHandler, TCQTProxyMarker {
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<Any?>?): Any? =
            method.invoke(base, *args.orEmpty())
    }
    class ForeignInvocation(var delegate: Callback) : InvocationHandler {
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<Any?>?): Any? =
            method.invoke(delegate, *args.orEmpty())
    }

    private fun unwrap(value: Any?) = ProxyChain.unwrap(value) { it is Callback || it is InvocationHandler }
    private fun proxy(handler: InvocationHandler) = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(Callback::class.java), handler,
    ) as Callback

    @Test
    fun removesConsecutiveOwnedWrappersAndAllowsNullBase() {
        val original = Original()
        assertSame(original, unwrap(Owned(Owned(original))))
        assertNull(unwrap(Owned(null)))
        assertNull(unwrap(null))
    }

    @Test
    fun retainsForeignWrapperAndOnlyReplacesItsOwnedDelegate() {
        val original = Original()
        val foreign = Foreign(Owned(original))
        assertSame(foreign, unwrap(foreign))
        assertSame(original, foreign.delegate)
    }

    @Test
    fun doesNotTraverseUnrelatedObjectGraphs() {
        val unrelated = Owned(Original())
        val foreign = Foreign(Original(), Holder(unrelated))
        unwrap(foreign)
        assertSame(unrelated, foreign.unrelated!!.callback)
    }

    @Test
    fun unwrapsOwnedBinderProxy() {
        val original = Original()
        assertSame(original, unwrap(proxy(OwnedInvocation(original))))
    }

    @Test
    fun preservesForeignBinderProxyAndRepairsItsDelegate() {
        val original = Original()
        val handler = ForeignInvocation(proxy(OwnedInvocation(original)))
        val foreign = proxy(handler)
        assertSame(foreign, unwrap(foreign))
        assertSame(original, handler.delegate)
    }

    @Test
    fun terminatesOnForeignCycles() {
        val cyclic = Foreign(Original())
        cyclic.delegate = cyclic
        assertSame(cyclic, unwrap(cyclic))
        assertSame(cyclic, cyclic.delegate)
    }

    @Test
    fun recognizesOwnedWrapperAcrossReloadClassLoaders() {
        val relocated = setOf(Owned::class.java.name, TCQTProxyMarker::class.java.name)
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name !in relocated) return super.loadClass(name, resolve)
                synchronized(this) {
                    val loaded = findLoadedClass(name) ?: run {
                        val bytes = getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                        defineClass(name, bytes, 0, bytes.size)
                    }
                    if (resolve) resolveClass(loaded)
                    return loaded
                }
            }
        }
        val original = Original()
        val old = loader.loadClass(Owned::class.java.name).getConstructor(Callback::class.java).newInstance(original)
        assertFalse(old is TCQTProxyMarker)
        assertSame(original, unwrap(old))
    }
}
