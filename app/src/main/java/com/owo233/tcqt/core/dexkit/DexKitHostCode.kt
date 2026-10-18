package com.owo233.tcqt.core.dexkit

/** A patch cache is reusable only when the running loader's DEX identity is known. */
internal data class DexKitHostCode(
    val isHotPatched: Boolean,
    val fingerprint: String? = null,
) {
    fun matches(cachedFingerprint: String?): Boolean =
        !isHotPatched || (!fingerprint.isNullOrEmpty() && fingerprint == cachedFingerprint)
}
