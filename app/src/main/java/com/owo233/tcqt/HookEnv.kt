package com.owo233.tcqt

import android.app.Application
import android.content.Context
import com.highcapable.kavaref.extension.toClass
import com.highcapable.kavaref.extension.toClassOrNull

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

    fun String.toHostClass() = toClass(hostClassLoader)

    fun String.toHostClassOrNull() = toClassOrNull(hostClassLoader)
}
