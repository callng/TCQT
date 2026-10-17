package com.owo233.tcqt.ui.component

/** Search the original string so Unicode case conversion cannot shift UTF-16 offsets. */
internal fun textMatchRanges(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    return buildList {
        var cursor = 0
        while (cursor <= text.length - query.length) {
            val index = text.indexOf(query, startIndex = cursor, ignoreCase = true)
            if (index < 0) break
            add(index until index + query.length)
            cursor = index + query.length
        }
    }
}
