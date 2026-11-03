package com.owo233.tcqt.features.debug

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

internal class Source32Capture(
    private val scan: () -> String,
    private val executor: Executor = Executor { command ->
        Thread(command, "tcqt-source32-scan").apply {
            isDaemon = true
            start()
        }
    },
) {

    @Volatile
    private var cached: String? = null
    private var active: CompletableFuture<String?>? = null
    private var onCapturedCallback: (() -> Unit)? = null

    // A signing callback can finish scanning before hookAfter returns its handle.
    fun onCaptured(callback: () -> Unit) {
        val captured = synchronized(this) {
            if (cached != null) {
                true
            } else {
                onCapturedCallback = callback
                false
            }
        }
        if (captured) callback()
    }

    fun begin(): CompletableFuture<String?> {
        cached?.let { return CompletableFuture.completedFuture(it) }
        return synchronized(this) {
            cached?.let { return@synchronized CompletableFuture.completedFuture(it) }
            active ?: CompletableFuture<String?>().also { future ->
                active = future
                try {
                    executor.execute {
                        val result = runCatching { scan() }.getOrNull()?.takeIf { value ->
                            value.length == 64 && value.all { it.digitToIntOrNull(16) != null }
                        }
                        val callback = synchronized(this) {
                            if (result != null) cached = result
                            if (active === future) active = null
                            if (result != null) {
                                onCapturedCallback.also { onCapturedCallback = null }
                            } else {
                                null
                            }
                        }
                        try {
                            callback?.invoke()
                        } finally {
                            future.complete(result)
                        }
                    }
                } catch (error: Exception) {
                    active = null
                    future.completeExceptionally(error)
                }
            }
        }
    }
}
