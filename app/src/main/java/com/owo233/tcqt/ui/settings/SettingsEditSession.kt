package com.owo233.tcqt.ui.settings

/** Persisted values and drafts; supplied maps may be Compose snapshot maps. */
internal class SettingsEditSession(
    private val persisted: MutableMap<String, Any> = linkedMapOf(),
    private val drafts: MutableMap<String, Any> = linkedMapOf(),
) {
    val changeCount: Int get() = drafts.size
    fun hasChange(key: String): Boolean = key in drafts
    fun boolean(key: String): Boolean = (drafts[key] ?: persisted[key]) as? Boolean ?: false
    fun int(key: String, fallback: Int = 0): Int = (drafts[key] ?: persisted[key]) as? Int ?: fallback
    fun string(key: String): String = (drafts[key] ?: persisted[key]) as? String ?: ""

    fun update(key: String, value: Any) {
        val previous = requireNotNull(persisted[key]) { "Unknown setting: $key" }
        require(previous.javaClass == value.javaClass) { "Wrong setting type: $key" }
        if (value == previous) drafts.remove(key) else drafts[key] = value
    }

    fun reload(values: Map<String, Any>) {
        persisted.clear()
        persisted.putAll(values)
        drafts.clear()
    }

    /** A failed write keeps the drafts and baseline intact. */
    fun save(commit: (Map<String, Any>) -> Unit) {
        val changes = drafts.toMap()
        if (changes.isEmpty()) return
        commit(changes)
        persisted.putAll(changes)
        changes.forEach { (key, value) -> if (drafts[key] == value) drafts.remove(key) }
    }
}
