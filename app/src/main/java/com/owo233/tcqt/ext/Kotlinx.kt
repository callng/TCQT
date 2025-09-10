package com.owo233.tcqt.ext

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.owo233.tcqt.utils.logE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val EMPTY_BYTE_ARRAY = ByteArray(0)

internal lateinit var globalUi: Handler

internal fun Context.toast(msg: String, flag: Int = Toast.LENGTH_SHORT) {
    if (!::globalUi.isInitialized) {
        globalUi = Handler(Looper.getMainLooper())
    }
    globalUi.post { Toast.makeText(this, msg, flag).show() }
}

class Nullable<T: Any>(
    private var value: T?
) {
    fun get(): T {
        return value!!
    }

    fun getOrNull(): T? {
        return value
    }

    fun isNull(): Boolean {
        return value == null
    }

    fun isNotNull(): Boolean {
        return value != null
    }

    fun set(value: T?) {
        this.value = value
    }
}

fun <T: Any> nullableOf(data: T? = null): Nullable<T> {
    return Nullable(data)
}

fun ByteArray.sliceSafe(off: Int, length: Int = size - off): ByteArray {
    if (isEmpty() || off >= size || length <= 0) return EMPTY_BYTE_ARRAY

    val safeLen = minOf(length, size - off)
    return ByteArray(safeLen).also {
        System.arraycopy(this, off, it, 0, safeLen)
    }
}

@JvmOverloads
fun ByteArray?.toHexString(uppercase: Boolean = false): String {
    if (this == null) return "null"

    val hexChars = if (uppercase) "0123456789ABCDEF" else "0123456789abcdef"
    val result = StringBuilder(this.size * 2)

    for (b in this) {
        val i = b.toInt() and 0xFF
        result.append(hexChars[i ushr 4])
        result.append(hexChars[i and 0x0F])
    }

    return result.toString()
}

fun String?.ifNullOrEmpty(defaultValue: () -> String?): String? {
    return if (this.isNullOrEmpty()) defaultValue() else this
}

@JvmOverloads
fun String.hex2ByteArray(replace: Boolean = false): ByteArray {
    val hex = if (replace) {
        buildString(length) {
            for (c in this@hex2ByteArray) {
                if (!c.isWhitespace()) append(c)
            }
        }
    } else this

    if (hex.length % 2 != 0) {
        throw IllegalArgumentException("Hex string length must be even: $hex")
    }

    val result = ByteArray(hex.length / 2)
    for (i in result.indices) {
        val hi = hex[i * 2].digitToIntOrNull(16)
        val lo = hex[i * 2 + 1].digitToIntOrNull(16)
        if (hi == null || lo == null) {
            throw IllegalArgumentException("Invalid hex character in: $hex at index ${i * 2}")
        }
        result[i] = ((hi shl 4) or lo).toByte()
    }
    return result
}

fun CoroutineScope.launchWithCatch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    errorDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    onError: suspend (Throwable) -> Unit = { e -> logE(msg = "launchWithCatch 异常", cause = e) },
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(context, start) {
        try {
            block()
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                launch(errorDispatcher + NonCancellable) {
                    onError(e)
                }
            } else {
                throw e
            }
        }
    }
}

fun Int.dp2px(ctx: Context): Int {
    return (this * ctx.getDensity() + 0.5f).toInt()
}

fun Context.getDensity(): Float {
    return this.resources.displayMetrics.density
}

fun String.toUtf8ByteArray(): ByteArray = this.toByteArray(Charsets.UTF_8)

fun <T> runRetry(
    retryNum: Int,
    sleepMs: Long = 0,
    exponentialBackoff: Boolean = false,
    jitter: Boolean = false,
    onError: ((Exception, attempt: Int) -> Unit)? = null,
    block: () -> T?
): T? {
    var lastException: Exception? = null
    var currentSleep = sleepMs

    for (i in 1..retryNum) {
        lastException = null

        try {
            val  result = block()
            if (result != null) {
                return result
            }
        } catch (e: Exception) {
            lastException = e
            onError?.invoke(e, i)
            // logE(msg = "runRetry failed on attempt $i: ${e.message}", cause = e)
        }

        if (i < retryNum) {
            val sleepTime = when {
                exponentialBackoff -> (currentSleep * 2).also { currentSleep = it }
                else -> currentSleep
            } + if (jitter) (Math.random() * 50).toLong() else 0

            if (sleepTime > 0) Thread.sleep(sleepTime)
        }
    }

    if (lastException != null) {
        logE(msg = "runRetry failed after $retryNum attempts", cause = lastException)
    }
    return null
}
