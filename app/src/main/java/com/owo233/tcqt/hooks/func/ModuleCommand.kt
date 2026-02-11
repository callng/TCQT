package com.owo233.tcqt.hooks.func

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.MMKVUtils
import com.owo233.tcqt.utils.log.Log
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onRun(ctx: Context, process: ActionProcess) {
        val intentFilter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exitApp" -> {
                        MobileQQ.getMobileQQ()?.takeIf {
                            it.isRuntimeReady
                        }?.run {
                            otherProcessExit(false)
                            qqProcessExit(true)
                        }
                    }

                    "config_clear" -> {
                        try {
                            MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME).also { it.clearAll() }
                        } catch (t: Throwable) {
                            Log.e("ModuleCommand onReceive config_clear error", t)
                        }
                    }
                }
            }
        }

        MobileQQ.getMobileQQ().registerReceiver(receiver, intentFilter)
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
