package com.owo233.tcqt

import android.app.Application
import android.content.Context
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.log.Log
import com.tencent.mobileqq.vas.theme.api.ThemeUtil

internal object HookEnv {

    const val TIM_PACKAGE = "com.tencent.tim"
    const val QQ_PACKAGE = "com.tencent.mobileqq"

    val moduleClassLoader: ClassLoader = HookEntry::class.java.classLoader!!

    lateinit var hostAppPackageName: String
        private set

    lateinit var processName: String
        private set

    lateinit var moduleApkPath: String
        private set

    lateinit var hostApkPath: String
        private set

    lateinit var appName: String
        private set

    lateinit var versionName: String
        private set

    var versionCode: Long = -1L
        private set

    lateinit var hostAppContext: Context
        private set

    lateinit var application: Application
        private set

    lateinit var hostClassLoader: ClassLoader
        private set

    private var targetSdkVersion: Int = 0

    fun setApplication(app: Application) {
        application = app
    }

    fun setHostClassLoader(cl: ClassLoader) {
        hostClassLoader = cl
    }

    fun setHostApkPath(path: String) {
        hostApkPath = path
    }

    fun setVersionName(v: String) {
        versionName = v
    }

    fun setVersionCode(code: Long) {
        versionCode = code
    }

    fun setHostAppContext(ctx: Context) {
        hostAppContext = ctx
    }

    fun setModuleApkPath(path: String) {
        moduleApkPath = path
    }

    fun setProcessName(p: String) {
        processName = p
    }

    fun setHostAppPackageName(pkg: String) {
        hostAppPackageName = pkg
    }

    fun setAppName(name: String) {
        appName = name
    }

    fun isTim() = hostAppPackageName == TIM_PACKAGE

    fun isQQ() = hostAppPackageName == QQ_PACKAGE

    fun isMainProcess() = ::processName.isInitialized &&
            ::hostAppPackageName.isInitialized &&
            processName == hostAppPackageName

    fun requireMinQQVersion(versionCode: Long): Boolean {
        return this.isQQ() && this.versionCode >= versionCode
    }

    fun requireMinTimVersion(versionCode: Long): Boolean {
        return this.isTim() && this.versionCode >= versionCode
    }

    fun String.toHostClass(): Class<*> = loadOrThrow(this)

    fun String.toHostClassOrNull(): Class<*>? = load(this).also {
        if (it == null) {
            Log.e("class: $this not found")
        }
    }

    fun isNightMode(): Boolean {
        if (ThemeUtil.isNowThemeIsNight(null, true, null)) return true
        return if (isQQ() && requireMinQQVersion(QQVersion.QQ_9_2_55_BETA_32895)) {
            ThemeUtil.isThemeNightModeV2()
        } else {
            false
        }
    }

    fun getTargetSdkVersion(): Int {
        if (targetSdkVersion > 0) {
            return targetSdkVersion
        }
        targetSdkVersion = 31
        try {
            targetSdkVersion = hostAppContext.applicationInfo.targetSdkVersion
        } catch (e: Exception) {
            Log.e("getTargetSdkVersion error", e)
        }
        return targetSdkVersion
    }
}
