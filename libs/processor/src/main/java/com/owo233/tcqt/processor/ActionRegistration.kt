package com.owo233.tcqt.processor

internal data class ActionRegistration(val name: String, val processes: List<String>, val priority: String)

internal class RegistrationTable {
    private val entries = sortedMapOf<String, Pair<String, ActionRegistration>>()
    fun add(source: String, entry: ActionRegistration): Boolean {
        val previous = entries[entry.name]
        if (previous != null && previous != (source to entry)) return false
        entries[entry.name] = source to entry
        return true
    }
    fun values(): List<ActionRegistration> = entries.values.map { it.second }
}

/** Mirrors the instance forms supported by ActionRegistry, without resolving or constructing them. */
internal fun constructionError(kind: String, inaccessible: Boolean, inner: Boolean, generic: Boolean,
                               abstract: Boolean, companion: Boolean, noArgConstructor: Boolean): String? = when {
    inaccessible -> "Registered action and its enclosing declarations must be public or internal"
    inner || generic || abstract || companion -> "Registered action must be concrete, non-inner, non-generic and not a companion object"
    kind == "OBJECT" -> null
    kind == "CLASS" && noArgConstructor -> null
    else -> "Registered action must be an object or a class with a JVM no-argument constructor"
}
