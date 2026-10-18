package com.owo233.tcqt.host.service

/** Ordered, bounded history; claiming an event is atomic with respect to duplicate callbacks. */
internal class RecallEventHistory<K>(private val capacity: Int) {
    init {
        require(capacity > 0)
    }

    private val entries = LinkedHashSet<K>()

    @Synchronized
    fun add(key: K): Boolean {
        if (!entries.add(key)) return false
        while (entries.size > capacity) entries.remove(entries.first())
        return true
    }

    @Synchronized
    fun restore(keys: Collection<K>) {
        entries.clear()
        keys.forEach(::add)
    }

    @Synchronized
    fun containsAny(keys: Set<K>): Boolean = keys.any(entries::contains)

    @Synchronized
    fun snapshot(): List<K> = entries.toList()
}
