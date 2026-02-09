package com.owo233.tcqt.hooks.func

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.MMKVUtils
import mqq.app.MobileQQ

@RegisterAction
class ModuleCommand : AlwaysRunAction() {

    companion object {
        private const val ACTION_MODULE_COMMAND = "com.owo233.tcqt.MODULE_COMMAND"

        fun sendCommand(ctx: Context, command: String) {
            Intent(ACTION_MODULE_COMMAND).apply {
                putExtra("cmd", command)
                setPackage(ctx.packageName)
            }.also {
                ctx.sendBroadcast(it)
            }
        }
    }

    override fun onRun(ctx: Context, process: ActionProcess) {
        val intentFilter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exitApp" -> {
                        MobileQQ.getMobileQQ().otherProcessExit(false)
                        MobileQQ.getMobileQQ().qqProcessExit(true)
                    }
                    "config_clear" -> MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME).also { it.clearAll() }
                }
            }
        }

        ContextCompat.registerReceiver(ctx, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
