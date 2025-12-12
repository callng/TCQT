package com.owo233.tcqt.ext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
    if (this == null || this.isEmpty()) return ""

    val hexChars = if (uppercase) "0123456789ABCDEF" else "0123456789abcdef"
    return StringBuilder(this.size * 2).apply {
        for (b in this@toHexString) {
            val i = b.toInt() and 0xFF
            append(hexChars[i ushr 4])
            append(hexChars[i and 0x0F])
        }
    }.toString()
}

fun String?.ifNullOrEmpty(defaultValue: () -> String): String {
    return if (this.isNullOrEmpty()) defaultValue() else this
}

@JvmOverloads
fun String.hex2ByteArray(replace: Boolean = false): ByteArray {
    val s = if (replace) this.replace(" ", "")
        .replace("\n", "")
        .replace("\t", "")
        .replace("\r", "") else this
    val bs = ByteArray(s.length / 2)
    for (i in 0 until s.length / 2) {
        bs[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bs
}

fun CoroutineScope.launchWithCatch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    errorDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    onError: suspend (Throwable) -> Unit = { e -> Log.e("launchWithCatch 异常", e) },
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
        Log.e("runRetry failed after $retryNum attempts", lastException)
    }
    return null
}

fun Context.copyToClipboard(str: String, showToast: Boolean = true) = runCatching {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).also {
        val cd = ClipData.newPlainText("text", str)
        it.setPrimaryClip(cd)
        if (showToast) Toasts.success(this, "已复制到剪贴板")
    }
}.onFailure {
    Log.e("复制到剪贴板失败", it)
    if (showToast) Toasts.error(this, "复制到剪贴板失败")
}

fun Context.clearClipboard() = runCatching {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).also {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            it.clearPrimaryClip()
        } else {
            it.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

inline fun AtomicBoolean.runOnce(block: () -> Unit) {
    if (compareAndSet(false, true)) block()
}

inline fun AtomicBoolean.runOnceSafe(block: () -> Unit): Result<Unit> {
    return if (compareAndSet(false, true)) {
        try {
            block()
            Result.success(Unit)
        } catch (e: Exception) {
            set(false)
            Result.failure(e)
        }
    } else {
        Result.success(Unit)
    }
}

fun Context.getAppSignature(pkgName: String): String = runCatching {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(
            pkgName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(
            pkgName,
            PackageManager.GET_SIGNATURES
        )
    }

    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        packageInfo.signatures
    }

    signatures?.firstOrNull()?.toCharsString() ?: ""
}.getOrElse { "" }
