package com.owo233.tcqt.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.ext.XpClassLoader
import com.tencent.qphone.base.util.BaseApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mqq.app.MobileQQ
import kotlin.random.Random

object PlatformTools {

    const val QQ_9_0_70_VER = 6700L
    const val TIM_4_0_95_VER = 4002L

    internal lateinit var GlobalUi: Handler

    fun isQQNt(): Boolean {
        return try {
            XpClassLoader.load("com.tencent.qqnt.base.BaseActivity") != null
        } catch (_: Exception) {
            false
        }
    }

    fun getQQVersion(ctx: Context = MobileQQ.getContext()): String {
        val packageInfo: PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return packageInfo.versionName ?: "unknown"
    }

    fun getQQVersionCode(ctx: Context = MobileQQ.getContext()): Long {
        val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    fun getClientVersion(ctx: Context = MobileQQ.getContext()): String = "android ${getQQVersion(ctx)}"

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
        return MobileQQ.PACKAGE_NAME == "com.tencent.mobileqq"
    }

    fun isMqqPackage(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.startsWith("com.tencent.mobileqq")
    }

    fun isTim(): Boolean {
        return MobileQQ.PACKAGE_NAME == "com.tencent.tim"
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

    private fun Context.toast(msg: String, flag: Int = Toast.LENGTH_SHORT) {
        if (!::GlobalUi.isInitialized) {
            logI(msg = msg)
            return
        }
        GlobalUi.post { Toast.makeText(this, msg, flag).show() }
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
