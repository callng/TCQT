package com.owo233.tcqt.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json

internal class SettingsSearchState(
    private val readHistory: () -> String,
    private val writeHistory: (String) -> Unit,
    private val failure: (Throwable) -> Unit,
) {
    var query by mutableStateOf("")
        private set
    var active by mutableStateOf(false)
        private set
    val history = mutableStateListOf<String>()
    private val editedQueries = linkedSetOf<String>()

    fun load() {
        runCatching {
            val encoded = readHistory()
            val values = if (encoded.isBlank()) emptyList() else Json.decodeFromString<List<String>>(encoded)
            history.clear()
            history.addAll(values.filter(String::isNotBlank).distinct().take(10))
        }.onFailure(failure)
    }
    fun enter() { active = true }
    fun exit() { active = false; query = "" }
    fun update(value: String) { query = value }
    fun rememberEdit() { if (active && query.isNotBlank()) editedQueries += query.trim() }
    fun add(value: String) {
        val word = value.trim()
        if (word.isEmpty()) return
        history.remove(word)
        history.add(0, word)
        while (history.size > 10) history.removeAt(history.lastIndex)
        persist()
    }
    fun commitQueries() { editedQueries.forEach(::add); editedQueries.clear() }
    fun reset() { history.clear(); editedQueries.clear() }
    fun clear() { reset(); persist() }
    private fun persist() { runCatching { writeHistory(Json.encodeToString(history.toList())) }.onFailure(failure) }
}

/** Matching uses the original text, preserving Unicode offsets for the UI highlighter. */
internal object FeatureSearch {
    data class Entry(val key: String, val label: String, val description: String, val order: Int)
    fun rank(entries: List<Entry>, query: String): List<String> {
        val word = query.trim()
        if (word.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val rank = when {
                entry.label.equals(word, true) || entry.description.equals(word, true) -> 3
                entry.label.contains(word, true) -> 2
                entry.description.contains(word, true) -> 1
                else -> 0
            }
            if (rank == 0) null else rank to entry
        }.sortedWith(compareByDescending<Pair<Int, Entry>> { it.first }.thenBy { it.second.order })
            .map { it.second.key }
    }
}
