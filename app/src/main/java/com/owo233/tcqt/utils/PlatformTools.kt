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
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.toast
import com.owo233.tcqt.hooks.base.PACKAGE_NAME_QQ
import com.owo233.tcqt.hooks.base.PACKAGE_NAME_TIM
import com.owo233.tcqt.hooks.base.hostInfo
import com.tencent.qphone.base.util.BaseApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mqq.app.MobileQQ
import kotlin.random.Random

object PlatformTools {

    const val QQ_9_0_70_VER = 6700L
    const val QQ_9_1_52_VER = 9054L
    const val QQ_9_1_90_26520 = 10248L
    const val QQ_9_2_23_30095 = 11684L
    const val TIM_4_0_95_VER = 4002L

    fun isNt(): Boolean {
        return try {
            XpClassLoader.load("com.tencent.qqnt.base.BaseActivity") != null
        } catch (_: Exception) {
            false
        }
    }

    fun getHostVersion(ctx: Context = MobileQQ.getContext()): String {
        val packageInfo: PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return packageInfo.versionName ?: "unknown"
    }

    fun getHostChannel(ctx: Context = MobileQQ.getContext()): String {
        // "537309838#3F9351D357E4AFF5#2017#GuanWang#fffffffffffffffffffffffffffff"
        val application = ctx.packageManager.getApplicationInfo(ctx.packageName, 128)
        return application.metaData!!.getString("AppSetting_params")!!.split("#")[3]
    }

    fun getHostVersionCode(ctx: Context = MobileQQ.getContext()): Long {
        val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    fun getHostName(): String = hostInfo.hostName

    fun getClientVersion(ctx: Context = MobileQQ.getContext()): String = "android ${getHostVersion(ctx)}"

    fun isMsfProcess(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.contains("msf", ignoreCase = true)
    }

    fun isToolProcess(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.contains("tool", ignoreCase = true)
    }

    fun isOpenSdkProcess(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.contains("openSdk", ignoreCase = true)
    }

    fun isMqq(): Boolean {
        return MobileQQ.PACKAGE_NAME == PACKAGE_NAME_QQ
    }

    fun isMqqPackage(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.startsWith(PACKAGE_NAME_QQ)
    }

    fun isTim(): Boolean {
        return MobileQQ.PACKAGE_NAME == PACKAGE_NAME_TIM
    }

    fun isMainProcess(): Boolean {
        return !MobileQQ.getMobileQQ().qqProcessName.contains(":")
    }

    @SuppressLint("HardwareIds")
    fun getAndroidID(): String {
        var androidId =
            Settings.Secure.getString(MobileQQ.getContext().contentResolver, "android_id")
        if (androidId == null) {
            val sb = StringBuilder()
            for (i in 0..15) {
                sb.append(Random.nextInt(10))
            }
            androidId = sb.toString()
        }
        return androidId
    }

    fun copyToClipboard(context: Context = MobileQQ.getContext(), text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
        context.toast("已复制到剪切板")
    }

    fun isMsfProcessRunning(context: Context = MobileQQ.getContext()): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses
        return runningProcesses.any { it.processName.contains("msf", ignoreCase = true) }
    }

    fun killMsfProcess(context: Context = MobileQQ.getContext()) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses
        runningProcesses.forEach {
            if (it.processName.contains("msf", ignoreCase = true)) {
                Process.killProcess(it.pid)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun restartMsfProcess(context: Context = MobileQQ.getContext()) {
        killMsfProcess(context)
        GlobalScope.launch(Dispatchers.Main) {
            val componentName = ComponentName(BaseApplication.getContext().packageName, "com.tencent.mobileqq.msf.service.MsfService")
            val intent = Intent()
            intent.component = componentName
            intent.putExtra("to_SenderProcessName", MobileQQ.PACKAGE_NAME)
            BaseApplication.getContext().startService(intent)
        }
    }
}
