package com.owo233.tcqt.features.appearance.liquidglass

import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/** A renderer's draw listeners must stop before the native hierarchy is restored. */
internal class ViewListenerScope {
    private val removers = mutableListOf<() -> Unit>()
    private var generation = 0
    fun preDraw(view: View, callback: () -> Boolean) {
        val observer = WeakReference(view.viewTreeObserver)
        val listener = ViewTreeObserver.OnPreDrawListener(callback)
        view.viewTreeObserver.addOnPreDrawListener(listener)
        removers += { observer.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener) }
    }
    fun onDetached(view: View, callback: () -> Unit) {
        val ref = WeakReference(view)
        val token = generation
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (token == generation && ref.get()?.isAttachedToWindow == false) callback()
                }
            }
        }
        view.addOnAttachStateChangeListener(listener)
        removers += { ref.get()?.removeOnAttachStateChangeListener(listener) }
    }
    fun clear() { generation++; removers.forEach { it() }; removers.clear() }
}
