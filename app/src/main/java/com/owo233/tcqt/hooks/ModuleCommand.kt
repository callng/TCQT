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
import com.owo233.tcqt.utils.logE
import com.tencent.mmkv.MMKV
import mqq.app.MobileQQ

@RegisterAction
class ModuleCommand: AlwaysRunAction() {

    private var registeredReceiver: BroadcastReceiver? = null

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

    override fun onRun(ctx: Context, process: ActionProcess) {
        val filter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exitApp" -> {
                        if (process == ActionProcess.MAIN) {
                            MobileQQ.getMobileQQ().qqProcessExit(true)
                        }
                    }
                    "config_clear" -> {
                        val config: MMKV = MMKVUtils.mmkvWithId("TCQT")
                        config.clearAll()
                    }
                }
            }
        }

        runCatching {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registeredReceiver = receiver
        }.onFailure {
            logE(msg = "registerReceiver error", cause = it)
        }
    }

    override val name: String get() = "模块主动指令"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
