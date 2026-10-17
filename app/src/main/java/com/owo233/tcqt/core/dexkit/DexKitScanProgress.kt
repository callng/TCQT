package com.owo233.tcqt.core.dexkit

internal data class DexKitScanState(
    val total: Int,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val finished: Boolean = false,
    val restartRequired: Boolean = false,
    val errorMessage: String? = null,
) {
    val failed: Int get() = completed - succeeded
    val canDismiss: Boolean get() = finished && !restartRequired
}

/** Count descriptors rather than tasks: several tasks can share the same cache key. */
internal class DexKitScanProgress(
    private val keys: Set<String>,
    private val onUpdate: (DexKitScanState) -> Unit,
) {
    private val outcomes = mutableMapOf<String, Boolean>()

    fun record(key: String, succeeded: Boolean) {
        if (key !in keys) return
        // A later task may supply a fallback for an earlier failed lookup.
        outcomes[key] = outcomes[key] == true || succeeded
        onUpdate(snapshot())
    }

    fun finish(restartRequired: Boolean, errorMessage: String? = null) {
        keys.forEach { outcomes.putIfAbsent(it, false) }
        onUpdate(snapshot().copy(
            finished = true,
            restartRequired = restartRequired,
            errorMessage = errorMessage,
        ))
    }

    fun finish(previousCache: Map<String, String>, savedCache: Map<String, String>) {
        finish(restartRequired = previousCache != savedCache)
    }

    private fun snapshot() = DexKitScanState(
        total = keys.size,
        completed = outcomes.size,
        succeeded = outcomes.count { it.value },
    )
}
