package com.owo233.tcqt.features.menu

internal class ReactionEmojiCatalog(
    codes: IntArray,
    unicodeToLocal: Map<Int, Int>,
    preferredOrder: List<Int>,
    configuredMappings: Map<Int, Int> = emptyMap(),
) {
    // QQ's array uses local IDs; the downloaded emoji map uses Unicode keys.
    val unicodeByLocalId: Map<Int, Int> = buildMap {
        codes.forEachIndexed { localId, unicode -> put(localId, unicode) }
        unicodeToLocal.forEach { (unicode, localId) -> putIfAbsent(localId, unicode) }
        configuredMappings.forEach { (localId, unicode) -> putIfAbsent(localId, unicode) }
    }.filter { (localId, unicode) ->
        localId >= 0 && unicode in 1..0x10ffff && unicode !in 0xd800..0xdfff
    }

    val localIds: ArrayList<Int> = arrayListOf<Int>().apply {
        val seen = HashSet<Int>()
        for (localId in preferredOrder + unicodeByLocalId.keys) {
            val unicode = unicodeByLocalId[localId] ?: continue
            if (seen.add(unicode)) add(localId)
        }
    }

    val localIdByUnicode: Map<Int, Int> = localIds.associateBy { unicodeByLocalId.getValue(it) }
}
