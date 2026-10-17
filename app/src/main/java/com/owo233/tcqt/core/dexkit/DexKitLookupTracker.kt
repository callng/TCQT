package com.owo233.tcqt.core.dexkit

class DexKitLookupTracker(
    private val onCompleted: (String, Boolean) -> Unit = { _, _ -> },
) {
    private val succeeded = mutableSetOf<String>()

    fun isSucceeded(name: String): Boolean = name in succeeded

    fun markSucceeded(name: String) {
        succeeded += name
    }

    fun markAllSucceeded(names: Collection<String>) {
        succeeded += names
    }

    fun markCompleted(name: String) {
        onCompleted(name, isSucceeded(name))
    }
}
