package com.owo233.tcqt.ext

import android.os.Looper
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.log.ActionErrorStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal object ModuleScope : CoroutineScope {

    private fun exceptionHandler(tag: String): CoroutineExceptionHandler {
        val actionKey = ActionErrorStore.currentActionKey()
        return CoroutineExceptionHandler { _, throwable ->
            actionKey?.let { ActionErrorStore.report(it, "异步任务", throwable) }
            Log.e(tag, throwable)
        }
    }

    @Volatile
    private var job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default + exceptionHandler("ModuleScope")

    fun cancelAll() {
        val old = job
        job = SupervisorJob()
        old.cancel()
    }

    fun launchIO(tag: String = "IO", block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.IO + exceptionHandler(tag), block = block)

    fun launchMain(block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.Main.immediate, block = block)

    fun launchDelayed(delayMillis: Long, block: suspend CoroutineScope.() -> Unit) = launch {
        delay(delayMillis.milliseconds)
        block()
    }

    fun launchMainDelayed(delayMillis: Long, block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.Main.immediate) {
            delay(delayMillis.milliseconds)
            block()
        }

    suspend fun <T> onMain(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main.immediate, block)

    suspend fun <T> onIO(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.IO, block)

    val isMainThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()
}
