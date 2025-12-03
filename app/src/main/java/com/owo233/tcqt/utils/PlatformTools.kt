package com.owo233.tcqt.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Process
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.QQ_PACKAGE
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.ext.toast
import com.owo233.tcqt.hooks.ModuleCommand
import com.owo233.tcqt.hooks.base.load
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlin.system.exitProcess

object PlatformTools {

    const val QQ_9_0_70_VER = 6700L
    const val QQ_9_1_52_VER = 9054L
    const val TIM_4_0_95_VER = 4002L

    fun isNt(): Boolean {
        return try {
            load("com.tencent.qqnt.base.BaseActivity") != null
        } catch (_: Exception) {
            false
        }
    }

    fun getHostVersion(ctx: Context = HookEnv.hostAppContext): String {
        val packageInfo: PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return packageInfo.versionName ?: "unknown"
    }

    fun getHostChannel(ctx: Context = HookEnv.hostAppContext): String {
        // "537309838#3F9351D357E4AFF5#2017#GuanWang#fffffffffffffffffffffffffffff"
        val application = ctx.packageManager.getApplicationInfo(ctx.packageName, 128)
        return application.metaData!!.getString("AppSetting_params")!!.split("#")[3]
    }

    fun getHostVersionCode(ctx: Context = HookEnv.hostAppContext): Long {
        val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    fun getHostName(): String = HookEnv.appName

    fun getClientVersion(ctx: Context = HookEnv.hostAppContext): String = "android ${getHostVersion(ctx)}"

    fun isMsfProcess(): Boolean {
        return HookEnv.processName.contains("msf", ignoreCase = true)
    }

    fun isToolProcess(): Boolean {
        return HookEnv.processName.contains("tool", ignoreCase = true)
    }

    fun isOpenSdkProcess(): Boolean {
        return HookEnv.processName.contains("openSdk", ignoreCase = true)
    }

    fun isMqq(): Boolean {
        return HookEnv.isQQ()
    }

    fun isMqqPackage(): Boolean {
        return HookEnv.processName.startsWith(QQ_PACKAGE)
    }

    fun isTim(): Boolean {
        return HookEnv.isTim()
    }

    fun isMainProcess(): Boolean {
        return !HookEnv.processName.contains(":")
    }

    @SuppressLint("HardwareIds")
    fun getAndroidID(): String? {
        return Settings.Secure.getString(HookEnv.hostAppContext.contentResolver, "android_id")
    }

    fun copyToClipboard(context: Context = HookEnv.hostAppContext, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
        context.toast("已复制到剪切板")
    }

    fun isMsfProcessRunning(context: Context = HookEnv.hostAppContext): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses
        return runningProcesses.any { it.processName.contains("msf", ignoreCase = true) }
    }

    fun killMsfProcess(context: Context = HookEnv.hostAppContext) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses
        runningProcesses.forEach {
            if (it.processName.contains("msf", ignoreCase = true)) {
                Process.killProcess(it.pid)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun restartMsfProcess(context: Context = HookEnv.hostAppContext) {
        killMsfProcess(context)
        GlobalScope.launchWithCatch {
            val componentName = ComponentName(context.packageName, "com.tencent.mobileqq.msf.service.MsfService")
            val intent = Intent().apply {
                component = componentName
                putExtra("to_SenderProcessName", context.packageName)
            }
            context.startService(intent)
        }
    }

    fun restartHostApp(context: Context = HookEnv.hostAppContext) {
        // Step 1：让非主进程先退出
        ModuleCommand.sendCommand(context, "exitAppChild")

        // Step 2：给子进程一点时间
        Thread.sleep(120)

        // Step 3：枚举所有同包的非主进程并kill
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myPid = Process.myPid() // 这里的进程是主进程
        val pkg = context.packageName
        am.runningAppProcesses?.forEach { proc ->
            if (proc.processName.startsWith(pkg) && proc.pid != myPid) {
                Process.killProcess(proc.pid)
                exitProcess(0)
            }
        }

        // Step 4：最后 kill 主进程
        Process.killProcess(myPid)
        exitProcess(0)
    }
}
