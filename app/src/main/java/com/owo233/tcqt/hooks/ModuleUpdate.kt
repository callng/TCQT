package com.owo233.tcqt.hooks

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.logE
import kotlin.system.exitProcess

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
                            killAllProcess(ctx)
                            Thread.sleep(100)
                            exitProcess(0)
                        }
                    }
                }
            }
        }

        ctx.registerReceiver(companion, intent)
    }

    private  fun killProcess(pid: Int) {
        Process.killProcess(pid)
    }

    private fun killAllProcess(context: Context, targetPackage: String = context.packageName) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return

        val runningProcesses = activityManager.runningAppProcesses ?: return

        for (processInfo in runningProcesses) {
            val processName = processInfo.processName ?: continue
            val pid = processInfo.pid

            if (processName.startsWith(targetPackage)) {
                try {
                    killProcess(pid)
                } catch(e: Exception) {
                    logE(msg = "Failed to kill process $pid", cause = e)
                }
            }
        }
    }

    override val name: String get() = "模块更新干掉宿主"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
