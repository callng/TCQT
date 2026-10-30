package com.owo233.tcqt.ui.parasitic

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.callMethod
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.setObject

internal class ActivityLaunchHandler(
    val base: Handler.Callback?,
    private val activityThread: Any,
) : Handler.Callback, TCQTProxyMarker {

    override fun handleMessage(msg: Message): Boolean {
        runCatching {
            val record = msg.obj ?: return@runCatching
            when (msg.what) {
                100 -> restoreIntent(record, "intent") // Android 8.1
                159 -> restoreTransaction(record) // Android 9+
            }
        }.onFailure { Log.e("Activity proxy failed to restore launch intent", it) }
        return base?.handleMessage(msg) ?: false
    }

    private fun restoreTransaction(transaction: Any) {
        val items = LaunchTransactionAccess.items(transaction)
        for (item in items) {
            if (item == null || item.javaClass.name != "android.app.servertransaction.LaunchActivityItem") continue
            val original = restoreIntent(item, "mIntent") ?: continue
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) continue
            // Android 12 的 preExecute 可能已缓存 ActivityClientRecord；新版 token 属于 item。
            val token = LaunchTransactionAccess.token(item, transaction) as? IBinder ?: continue
            try {
                activityThread.callMethod("getLaunchingActivity", token)?.setObject("intent", original)
            } catch (e: NoSuchMethodException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw e
            }
        }
    }

    private fun restoreIntent(record: Any, field: String): Intent? {
        val wrapper = record.getObject(field) as? Intent ?: return null
        val original = ActivityProxyIntents.unwrap(wrapper) ?: return null
        record.setObject(field, original)
        return original
    }
}

/** 新旧事务访问集中在这里，避免 getCallbacks() 在新系统返回 null 而静默漏掉启动。 */
internal object LaunchTransactionAccess {
    fun items(transaction: Any): List<*> {
        val modern = try {
            transaction.callMethod("getTransactionItems") as? List<*>
        } catch (_: NoSuchMethodException) {
            null
        }
        if (modern != null) return modern
        return transaction.callMethod("getCallbacks") as? List<*> ?: emptyList<Any>()
    }

    fun token(item: Any, transaction: Any): Any? {
        val token = try {
            item.callMethod("getActivityToken")
        } catch (_: NoSuchMethodException) {
            null
        }
        return token ?: transaction.callMethod("getActivityToken")
    }
}
