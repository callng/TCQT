package com.owo233.tcqt.core.action

import java.util.concurrent.atomic.AtomicReference

/** Retains a startup plan until the same Application's original onCreate has returned successfully. */
class StartupAfterCreate<T : Any> {
    private val pending = AtomicReference<Pair<T, () -> Unit>?>()
    fun prepare(instance: T, callback: () -> Unit) {
        check(pending.compareAndSet(null, instance to callback)) { "Startup already pending" }
    }
    fun complete(instance: T, failure: Throwable?) {
        val entry = pending.get() ?: return
        if (entry.first === instance && pending.compareAndSet(entry, null) && failure == null) entry.second()
    }
}
