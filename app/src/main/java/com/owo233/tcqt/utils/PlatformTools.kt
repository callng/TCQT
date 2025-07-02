package com.owo233.tcqt.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.ext.XpClassLoader
import mqq.app.MobileQQ
import kotlin.random.Random

object PlatformTools {

    const val QQ_9_0_65_VER = 6566L
    const val QQ_9_0_70_VER = 6700L

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

    fun isMqq(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName == "com.tencent.mobileqq"
    }

    fun isMqqPackage(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName.startsWith("com.tencent.mobileqq")
    }

    fun isTim(): Boolean {
        return MobileQQ.getMobileQQ().qqProcessName == "com.tencent.tim"
    }

    fun isMainProcess(): Boolean {
        return isMqq() || isTim()
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
}
