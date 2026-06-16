package com.owo233.tcqt.loader

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.log.Log

internal object ReceiverRegistry {

    private val handles = linkedMapOf<BroadcastReceiver, Application>()

    @Synchronized
    fun register(app: Application, receiver: BroadcastReceiver, filter: IntentFilter, exported: Boolean = false) {
        runCatching { app.unregisterReceiver(receiver) }
        SyncUtils.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flag = if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
                app.registerReceiver(receiver, filter, flag)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
        }
        handles[receiver] = app
    }

    @Synchronized
    fun unregisterAll() {
        handles.forEach { (receiver, app) ->
            runCatching { app.unregisterReceiver(receiver) }.onFailure { e ->
                Log.e("ReceiverRegistry unregister failed", e)
            }
        }
        handles.clear()
    }
}
