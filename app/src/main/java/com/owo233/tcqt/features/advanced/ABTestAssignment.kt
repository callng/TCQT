package com.owo233.tcqt.features.advanced

/** 仅调整已有的 A/B 格式赋值；未下发、未知分组及未选中的实验保持原样。 */
internal object ABTestAssignment {
    fun parseNames(text: String): Set<String> = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    fun overrideOf(name: String?, current: String?, mode: Int, names: Set<String>): String? {
        if (name.isNullOrEmpty() || current.isNullOrEmpty()) return null
        if (mode != 1 && mode != 2) return null
        if (names.isNotEmpty() && name !in names) return null
        if (!current.equals("${name}_A", ignoreCase = true) &&
            !current.equals("${name}_B", ignoreCase = true)
        ) return null
        return "${name}_${if (mode == 1) "A" else "B"}"
    }
}
