package com.owo233.tcqt.core.hook

import java.util.concurrent.ConcurrentHashMap

/** Coherent publication, immutable callback snapshots, and idempotent removal. */
class HookLifecycle<K : Any, C : Any, V : Any>(
    private val prepare: (K) -> V,
    private val install: (Slot<K, C, V>) -> Boolean,
    private val uninstall: (Slot<K, C, V>) -> Boolean,
    private val order: Comparator<C>,
) {
    class Slot<K, C, V>(val id: Long, val key: K, val value: V, callbacks: List<C>) {
        @Volatile var callbacks: List<C> = callbacks
            internal set
    }

    private val active = HashMap<K, Slot<K, C, V>>()
    private val published = ConcurrentHashMap<Long, Slot<K, C, V>>()
    private var nextId = 1L

    @Synchronized fun register(key: K, callback: C): Unhook? {
        val current = active[key]
        val slot = if (current != null) {
            current.callbacks = (current.callbacks + callback).sortedWith(order)
            current
        } else {
            val created = Slot(nextId++, key, prepare(key), listOf(callback))
            // Native redirection can be reached before install() returns. Both
            // the backup and first callback must already be visible to dispatch.
            active[key] = created
            published[created.id] = created
            val installed = try { install(created) } catch (error: Throwable) {
                active.remove(key)
                published.remove(created.id)
                throw error
            }
            if (!installed) {
                active.remove(key)
                published.remove(created.id)
                return null
            }
            created
        }
        return Unhook { remove(slot, callback) }
    }

    @Synchronized private fun remove(slot: Slot<K, C, V>, callback: C) {
        if (active[slot.key] !== slot || slot.callbacks.none { it === callback }) return
        slot.callbacks = slot.callbacks.filterNot { it === callback }
        if (slot.callbacks.isEmpty() && uninstall(slot)) {
            active.remove(slot.key)
            // A call may have entered the trampoline before native restoration
            // and reach Java after it. Keep its backup, but release callbacks.
            // Reclaiming generated DEX requires native stack quiescence support.
        }
    }

    fun forId(id: Long): Slot<K, C, V>? = published[id]
    @Synchronized fun forKey(key: K): Slot<K, C, V>? = active[key]
}
