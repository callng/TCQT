package com.owo233.tcqt.core.dexkit

internal fun dexKitScanClockMillis(): Long = System.nanoTime() / 1_000_000

internal enum class DexKitScanPhase { PREPARING, SCANNING, SAVING }

internal data class DexKitFeatureScanState(
    val name: String,
    val total: Int,
    val completed: Int,
    val startedAtMillis: Long,
    val elapsedMillis: Long = 0,
)

internal data class DexKitScanState(
    val total: Int,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val finished: Boolean = false,
    val restartRequired: Boolean = false,
    val errorMessage: String? = null,
    val failedKeys: List<String> = emptyList(),
    val failedFeatureNames: List<String> = emptyList(),
    val phase: DexKitScanPhase = DexKitScanPhase.PREPARING,
    val startedAtMillis: Long? = null,
    val elapsedMillis: Long = 0,
    val currentFeature: DexKitFeatureScanState? = null,
    val featureTimings: List<DexKitFeatureScanState> = emptyList(),
) {
    val failed: Int get() = completed - succeeded
    val canDismiss: Boolean get() = finished && !restartRequired
    val slowFeatures: List<DexKitFeatureScanState>
        get() = featureTimings.filter { it.elapsedMillis > 1_500 }
            .sortedByDescending { it.elapsedMillis }.take(5)

    fun elapsedMillisAt(nowMillis: Long): Long =
        if (finished || startedAtMillis == null) elapsedMillis
        else (nowMillis - startedAtMillis).coerceAtLeast(elapsedMillis)
}

/** Count descriptors rather than tasks: several tasks can share the same cache key. */
internal class DexKitScanProgress(
    private val keys: Set<String>,
    private val featureNamesByKey: Map<String, List<String>> = emptyMap(),
    private val clockMillis: () -> Long = ::dexKitScanClockMillis,
    private val onUpdate: (DexKitScanState) -> Unit,
) {
    private val outcomes = mutableMapOf<String, Boolean>()
    private val startedAtMillis = clockMillis()
    private var phase = DexKitScanPhase.PREPARING
    private var currentFeature: DexKitFeatureScanState? = null
    private var currentFeatureKeys = emptySet<String>()
    private val currentFeatureCompletedKeys = mutableSetOf<String>()
    private val featureTimings = mutableListOf<DexKitFeatureScanState>()

    fun start() = onUpdate(snapshot())

    fun beginFeature(name: String, featureKeys: Set<String>) {
        endFeature()
        currentFeatureKeys = featureKeys.intersect(keys)
        currentFeatureCompletedKeys.clear()
        // Reuse successful shared lookups, but count failed ones again when retried.
        currentFeatureKeys.filterTo(currentFeatureCompletedKeys) { outcomes[it] == true }
        currentFeature = DexKitFeatureScanState(
            name = name,
            total = currentFeatureKeys.size,
            completed = currentFeatureCompletedKeys.size,
            startedAtMillis = clockMillis(),
        )
        phase = DexKitScanPhase.SCANNING
        onUpdate(snapshot())
    }

    fun endFeature(): DexKitFeatureScanState? {
        val feature = currentFeatureSnapshot(clockMillis()) ?: return null
        featureTimings += feature
        currentFeature = null
        currentFeatureKeys = emptySet()
        currentFeatureCompletedKeys.clear()
        onUpdate(snapshot())
        return feature
    }

    fun savingResults() {
        endFeature()
        phase = DexKitScanPhase.SAVING
        onUpdate(snapshot())
    }

    fun record(key: String, succeeded: Boolean) {
        if (key !in keys) return
        // A later task may supply a fallback for an earlier failed lookup.
        outcomes[key] = outcomes[key] == true || succeeded
        if (key in currentFeatureKeys) currentFeatureCompletedKeys += key
        onUpdate(snapshot())
    }

    fun finish(restartRequired: Boolean, errorMessage: String? = null) {
        endFeature()
        keys.forEach { outcomes.putIfAbsent(it, false) }
        onUpdate(snapshot().copy(
            finished = true,
            restartRequired = restartRequired,
            errorMessage = errorMessage,
        ))
    }

    fun finish(
        previousCache: Map<String, String>,
        savedCache: Map<String, String>,
        previousCacheUsable: Boolean = true,
    ) {
        // Code changes skip dependent hooks at startup, even if the new descriptors are identical.
        finish(restartRequired = previousCache != savedCache ||
            (!previousCacheUsable && savedCache.values.any { it.isNotEmpty() }))
    }

    private fun snapshot(): DexKitScanState {
        val nowMillis = clockMillis()
        val failedKeys = keys.filter { outcomes[it] == false }
        return DexKitScanState(
            total = keys.size,
            completed = outcomes.size,
            succeeded = outcomes.count { it.value },
            failedKeys = failedKeys,
            failedFeatureNames = failedKeys.flatMap { key ->
                featureNamesByKey[key].orEmpty().ifEmpty { listOf(key) }
            }.distinct(),
            phase = phase,
            startedAtMillis = startedAtMillis,
            elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0),
            currentFeature = currentFeatureSnapshot(nowMillis),
            featureTimings = featureTimings.toList(),
        )
    }

    private fun currentFeatureSnapshot(nowMillis: Long): DexKitFeatureScanState? =
        currentFeature?.let { feature ->
            feature.copy(
                completed = currentFeatureCompletedKeys.size,
                elapsedMillis = (nowMillis - feature.startedAtMillis).coerceAtLeast(0),
            )
        }
}
