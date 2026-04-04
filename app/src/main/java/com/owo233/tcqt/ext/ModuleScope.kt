package com.owo233.tcqt.ext

import android.os.Looper
import com.owo233.tcqt.utils.log.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal object ModuleScope : CoroutineScope {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("ModuleScope", throwable)
    }

    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.Default + exceptionHandler

    fun launchIO(tag: String = "IO", block: suspend CoroutineScope.() -> Unit) =
        launch(
            Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(tag, e) },
            block = block
        )

    fun launchMain(block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.Main.immediate, block = block)

    fun launchDelayed(delayMillis: Long, block: suspend CoroutineScope.() -> Unit) = launch {
        delay(delayMillis)
        block()
    }

    fun launchMainDelayed(delayMillis: Long, block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.Main.immediate) {
            delay(delayMillis)
            block()
        }

    suspend fun <T> onMain(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main.immediate, block)

    suspend fun <T> onIO(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.IO, block)

    val isMainThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()
}
