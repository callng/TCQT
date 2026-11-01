package com.owo233.tcqt.features.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ReceiverRegistry
import mqq.app.MobileQQ

@RegisterAction(processes = [ActionProcess.MAIN])
object ModuleCommand : InfraTask(
    key = "ModuleCommand",
) {

    override fun install() {
        val filter = IntentFilter(ModuleCommandBus.ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    ModuleCommandBus.CMD_RESTART -> {
                        MobileQQ.getMobileQQ()?.takeIf {
                            it.isRuntimeReady
                        }?.run {
                            HookEnv.resetApp()
                        }
                    }

                    ModuleCommandBus.CMD_EXIT -> {
                        MobileQQ.getMobileQQ()?.takeIf {
                            it.isRuntimeReady
                        }?.run {
                            otherProcessExit(false)
                            qqProcessExit(true)
                        }
                    }

                    ModuleCommandBus.CMD_CONFIG_CLEAR -> {
                        try {
                            TCQTSetting.clearAll()
                        } catch (t: Throwable) {
                            Log.e("ModuleCommand onReceive config_clear error", t)
                        }
                    }
                }
            }
        }

        ReceiverRegistry.register(hostApp, receiver, filter)
    }
}
