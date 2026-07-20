package com.owo233.tcqt.ext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.utils.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val EMPTY_BYTE_ARRAY = ByteArray(0)

private val HEX_CHARS_UPPER = "0123456789ABCDEF".toCharArray()
private val HEX_CHARS_LOWER = "0123456789abcdef".toCharArray()

internal fun Context.toast(msg: String, flag: Int = Toast.LENGTH_SHORT) {
    ModuleScope.launchMain {
        Toast.makeText(this@toast, msg, flag).show()
    }
}

class Nullable<T : Any>(
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

fun <T : Any> nullableOf(data: T? = null): Nullable<T> {
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
fun ByteArray?.toHexString(uppercase: Boolean = false, limit: Int = Int.MAX_VALUE): String {
    if (this == null || this.isEmpty()) return ""

    val originalSize = this.size
    val isTruncated = originalSize > limit
    val targetLen = if (isTruncated) limit else originalSize

    val hexChars = if (uppercase) HEX_CHARS_UPPER else HEX_CHARS_LOWER
    val result = CharArray(targetLen shl 1)

    var i = 0
    var j = 0

    while (i < targetLen) {
        val v = this[i].toInt() and 0xFF
        result[j++] = hexChars[v ushr 4]
        result[j++] = hexChars[v and 0x0F]
        i++
    }

    if (!isTruncated) return String(result)

    return String(result) + "... (truncated, total: $originalSize bytes)"
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
    val actionKey = com.owo233.tcqt.utils.log.ActionErrorStore.currentActionKey()
    return launch(context, start) {
        try {
            block()
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                actionKey?.let {
                    com.owo233.tcqt.utils.log.ActionErrorStore.report(it, "异步任务", e)
                }
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
    maxSleepMs: Long = 30_000L,
    onError: ((Exception, attempt: Int) -> Unit)? = null,
    block: () -> T?
): T? {
    var lastException: Exception? = null
    var currentSleep = sleepMs

    for (i in 1..retryNum) {
        lastException = null
        try {
            val result = block()
            if (result != null) return result
        } catch (e: Exception) {
            lastException = e
            onError?.invoke(e, i)
        }

        if (i < retryNum) {
            val jitterMs = if (jitter) (currentSleep * Math.random() * 0.2).toLong() else 0
            val sleepTime = currentSleep + jitterMs
            if (sleepTime > 0) Thread.sleep(sleepTime)

            if (exponentialBackoff) {
                currentSleep = minOf(currentSleep * 2, maxSleepMs)
            }
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
        if (showToast) Toasts.success("已复制到剪贴板")
    }
}.onFailure {
    Log.e("复制到剪贴板失败", it)
    if (showToast) Toasts.error("复制到剪贴板失败")
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

val Any?.shortClassName: String
    get() = when (this) {
        null -> "null"
        is String -> this.replace("/", ".").substringAfterLast('.')
        is Class<*> -> this.name.substringAfterLast('.')
        is Field -> this.type.name.substringAfterLast('.')
        else -> this.javaClass.name.substringAfterLast('.')
    }

/**
 * 检查指定索引的位标志是否启用
 * @param index 索引值，从0开始（0表示第1个选项，对应位值 1 shl 0 = 1）
 * @return true 该位已启用，false 未启用
 */
fun Int.isFlagEnabled(index: Int): Boolean {
    require(index >= 0) { "Index must be non-negative" }
    return (this and (1 shl index)) != 0
}

/**
 * 将 Int 转为大端序字节数组（4字节）
 * 例如：94124 -> [0x00, 0x01, 0x6F, 0xAC]
 */
fun Int.toBytes(): ByteArray {
    return byteArrayOf(
        (this shr 24 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this and 0xFF).toByte()
    )
}

/**
 * 将 Int 转为小端序字节数组（4字节）
 * 例如：94124 -> [0xAC, 0x6F, 0x01, 0x00]
 */
fun Int.toBytesLittleEndian(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 24 and 0xFF).toByte()
    )
}

/**
 * 将 Int 转为字节数组，并自动省略前导零（至少保留1个字节）
 * 例如：94124 -> [0x01, 0x6F, 0xAC]（省略前导0x00）
 */
fun Int.toCompactBytes(): ByteArray {
    var temp = this
    val list = mutableListOf<Byte>()
    do {
        list.add(0, (temp and 0xFF).toByte())
        temp = temp shr 8
    } while (temp != 0)
    return list.toByteArray()
}
