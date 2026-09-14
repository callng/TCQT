package com.owo233.tcqt.core.env

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.PixelCopy
import android.view.Window
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.owo233.tcqt.core.env.HookEnv.QQ_PACKAGE
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.isStatic
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.callMethod

object PlatformTools {

    private const val LOADING_ACTIVITY_CLASS =
        "com.tencent.mobileqq.login.restart.MainProcessRestartLoadingActivity"

    private const val COMPANION_INNER_CLASS = $$"$$LOADING_ACTIVITY_CLASS$a"

    private val companionInstance by lazy {
        runCatching {
            val outer = LOADING_ACTIVITY_CLASS.toHostClass()
            val inner = COMPANION_INNER_CLASS.toHostClass()

            outer.declaredFields
                .first { it.isStatic && it.type == inner }
                .apply { isAccessible = true }
                .get(null)
        }.onFailure {
            Log.e("Restart: Failed to get companion instance", it)
        }.getOrNull()
    }

    fun getHostVersion(ctx: Context = HookEnv.hostAppContext): String {
        val packageInfo: PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return packageInfo.versionName ?: "unknown"
    }

    fun getHostChannel(ctx: Context = HookEnv.hostAppContext): String {
        // "537309838#3F9351D357E4AFF5#2017#GuanWang#fffffffffffffffffffffffffffff"
        return try {
            val application = ctx.packageManager.getApplicationInfo(
                ctx.packageName,
                PackageManager.GET_META_DATA
            )
            val raw = application.metaData
                ?.getString("AppSetting_params")
                ?: return "Unknown"
            val parts = raw.split("#")
            parts.getOrNull(3) ?: "Unknown"
        } catch (e: Exception) {
            Log.e("Failed to get host channel", e)
            "Unknown"
        }
    }

    fun getClientVersion(ctx: Context = HookEnv.hostAppContext): String =
        "android ${getHostVersion(ctx)}"

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
        return HookEnv.isTIM()
    }

    fun isMainProcess(): Boolean {
        return !HookEnv.processName.contains(":")
    }

    @SuppressLint("HardwareIds")
    fun getAndroidID(): String? {
        return Settings.Secure.getString(HookEnv.hostAppContext.contentResolver, "android_id")
    }

    fun killSubProcesses(context: Context = HookEnv.hostAppContext) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = am.runningAppProcesses ?: return

        val packageName = HookEnv.hostAppPackageName
        val myPid = Process.myPid()

        for (processInfo in runningAppProcesses) {
            if (processInfo.uid == Process.myUid() &&
                processInfo.pid != myPid &&
                processInfo.processName != packageName) {

                Process.killProcess(processInfo.pid)
            }
        }
    }

    fun isHostWhitelisted(url: String): Boolean {
        val host = runCatching {
            val normalized = if (url.contains("://")) url else "http://$url"
            normalized.toUri().host?.lowercase()
        }.getOrNull() ?: return false

        return host.endsWith("qq.com") ||
                host.endsWith("tenpay.com") ||
                host.endsWith("tencent.com") ||
                host.endsWith("cdn-go.cn") ||
                host.endsWith("wechat.com")
    }

    fun reStartLoadingActivity() {
        val window = HostBridge.topActivity()?.window
        if (window == null) {
            Log.w("Restart: 顶层 Activity 不可用，跳过重启")
            return
        }
        captureScreenshot(window) { screenshot ->
            companionInstance?.also {
                it.callMethod("a", HookEnv.hostAppContext, screenshot, "重启中...")
            } ?: Log.w("Restart: companionInstance is null, skip restart")
        }
    }

    private fun captureScreenshot(window: Window, callback: (Bitmap?) -> Unit) {
        val bitmap = createBitmap(window.decorView.width, window.decorView.height)

        val locationOfViewInWindow = IntArray(2)
        window.decorView.getLocationInWindow(locationOfViewInWindow)

        try {
            PixelCopy.request(
                window,
                Rect(
                    locationOfViewInWindow[0],
                    locationOfViewInWindow[1],
                    locationOfViewInWindow[0] + window.decorView.width,
                    locationOfViewInWindow[1] + window.decorView.height
                ),
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        callback(bitmap)
                    } else {
                        callback(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (_: IllegalArgumentException) {
            callback(null)
        }
    }
}
