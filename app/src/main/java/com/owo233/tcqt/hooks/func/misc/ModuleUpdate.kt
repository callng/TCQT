package com.owo233.tcqt.hooks.func.misc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.func.ModuleCommand

@RegisterAction
@RegisterSetting(
    key = "module_update",
    name = "模块更新干掉宿主",
    type = SettingType.BOOLEAN,
    desc = "每次本模块更新后将自动重启（杀死）宿主进程。",
    uiTab = "杂项"
)
class ModuleUpdate : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val intent = IntentFilter()
        intent.addAction("android.intent.action.PACKAGE_ADDED")
        intent.addAction("android.intent.action.PACKAGE_REMOVED")
        intent.addAction("android.intent.action.PACKAGE_REPLACED")
        intent.addDataScheme("package")

        val companion = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when(intent.action) {
                    "android.intent.action.PACKAGE_ADDED",
                    "android.intent.action.PACKAGE_REMOVED",
                    "android.intent.action.PACKAGE_REPLACED" -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == TCQTBuild.APP_ID && process == ActionProcess.MAIN) {
                            ModuleCommand.Companion.sendCommand(ctx, "exitApp")
                        }
                    }
                }
            }
        }

        ctx.registerReceiver(companion, intent)
    }

    override val key: String get() = GeneratedSettingList.MODULE_UPDATE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
