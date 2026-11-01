package com.owo233.tcqt.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread selection/import lifecycle, independent of Android's picker and ContentResolver. */
internal class PngImportCoordinator<S>(
    private val scope: CoroutineScope,
    private val validKey: (String) -> Boolean,
    private val read: suspend (S) -> String,
    private val apply: (String, String) -> Unit,
    private val busy: (Boolean) -> Unit,
    private val failure: (Throwable) -> Unit,
) {
    private var selectedKey: String? = null
    private var job: Job? = null
    private var generation = 0L
    var isImporting: Boolean = false
        private set

    fun select(key: String): Boolean {
        if (isImporting || selectedKey != null || !validKey(key)) return false
        selectedKey = key
        return true
    }
    fun receive(source: S?) {
        val key = selectedKey ?: return
        selectedKey = null
        if (source == null) return
        val request = ++generation
        isImporting = true
        busy(true)
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val value = read(source)
                coroutineContext.ensureActive()
                if (generation == request) apply(key, value)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == request) failure(error)
            } finally {
                if (generation == request) {
                    job = null
                    isImporting = false
                    busy(false)
                }
            }
        }
        job = task
        task.start()
    }
    fun cancel() {
        generation++
        job?.cancel()
        job = null
        selectedKey = null
        isImporting = false
        busy(false)
    }
}
