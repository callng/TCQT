package com.owo233.tcqt.hooks.func.misc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        val updateReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when(intent.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == TCQTBuild.APP_ID) {
                            ModuleCommand.sendCommand(ctx, "exitApp")
                        }
                    }
                }
            }
        }

        ContextCompat.registerReceiver(ctx, updateReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override val key: String get() = GeneratedSettingList.MODULE_UPDATE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
