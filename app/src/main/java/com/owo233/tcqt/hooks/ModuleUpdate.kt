package com.owo233.tcqt.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.internals.setting.TCQTSetting
import kotlin.system.exitProcess

@RegisterAction
class ModuleUpdate: IAction {

    override fun onRun(ctx: Context) {
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
                        if (packageName == TCQTBuild.APP_ID) {
                            Thread.sleep(50)
                            exitProcess(0)
                        }
                    }
                }
            }
        }

        ctx.registerReceiver(companion, intent)
    }

    override val name: String get() = "模块更新干掉宿主"

    override val key: String get() = TCQTSetting.MODULE_UPDATE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
