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
import com.owo233.tcqt.ext.toast
import com.owo233.tcqt.hooks.base.load
import com.tencent.qphone.base.util.BaseApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
        GlobalScope.launch(Dispatchers.Main) {
            val componentName = ComponentName(BaseApplication.getContext().packageName, "com.tencent.mobileqq.msf.service.MsfService")
            val intent = Intent()
            intent.component = componentName
            intent.putExtra("to_SenderProcessName", HookEnv.currentHostAppPackageName)
            BaseApplication.getContext().startService(intent)
        }
    }
}
