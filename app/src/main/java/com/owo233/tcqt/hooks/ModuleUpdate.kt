package com.owo233.tcqt.hooks

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

@RegisterAction
@RegisterSetting(key = "module_update", name = "模块更新干掉宿主", type = SettingType.BOOLEAN)
class ModuleUpdate: IAction {

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
                            ModuleCommand.sendCommand(ctx, "exitApp")
                        }
                    }
                }
            }
        }

        ctx.registerReceiver(companion, intent)
    }

    override val key: String get() = GeneratedSettingList.MODULE_UPDATE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
