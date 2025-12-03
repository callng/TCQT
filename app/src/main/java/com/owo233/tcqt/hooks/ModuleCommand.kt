package com.owo233.tcqt.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.MMKVUtils
import com.owo233.tcqt.utils.PlatformTools
import com.tencent.mmkv.MMKV
import kotlin.system.exitProcess

@RegisterAction
class ModuleCommand : AlwaysRunAction() {

    private var registeredReceiver: BroadcastReceiver? = null

    override fun onRun(ctx: Context, process: ActionProcess) {
        val filter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exitAppChild" -> {
                        if (!ProcUtil.isMain) {
                            Process.killProcess(Process.myPid())
                            exitProcess(0)
                        }
                    }
                    "exitApp" -> {
                        if (ProcUtil.isMain) {
                            PlatformTools.restartHostApp()
                        }
                    }
                    "config_clear" -> {
                        if (process == ActionProcess.MAIN) {
                            val config: MMKV = MMKVUtils.mmkvWithId("TCQT")
                            config.clearAll()
                        }
                    }
                }
            }
        }

        runCatching {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_NOT_EXPORTED
            } else {
                ContextCompat.RECEIVER_EXPORTED
            }
            ctx.registerReceiver(receiver, filter, flag)
            registeredReceiver = receiver
        }.onFailure {
            Log.e(msg = "registerReceiver error", it)
        }
    }

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

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
