package com.owo233.tcqt.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.MMKVUtils
import com.tencent.mmkv.MMKV
import kotlin.system.exitProcess

@RegisterAction
class ModuleCommand: AlwaysRunAction() {

    companion object {
        private const val ACTION_MODULE_COMMAND = "com.owo233.tcqt.MODULE_COMMAND"

        fun sendCommand(ctx: Context, command: String) {
            val intent = Intent(ACTION_MODULE_COMMAND).apply {
                putExtra("cmd", command)
                setPackage(ctx.packageName)
            }
            ctx.sendBroadcast(intent)
        }
    }

    override fun onRun(ctx: Context) {
        val filter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exit_all" -> {
                        Thread.sleep(50)
                        exitProcess(0)
                    }
                    "config_clear" -> {
                        val config: MMKV = MMKVUtils.mmkvWithId("TCQT")
                        config.clearAll()
                    }
                }
            }
        }

        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override val name: String get() = "模块主动指令"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
