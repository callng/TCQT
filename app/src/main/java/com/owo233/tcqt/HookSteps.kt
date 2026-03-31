package com.owo233.tcqt

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.loader.HybridClassLoader
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.lifecycle.ParasiticActivity
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.log.Log
import de.robv.android.xposed.callbacks.XC_LoadPackage

internal object HookSteps {

    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setHostAppPackageName(loadPackageParam.packageName)
    }

    fun initHandleLoadPackage(processName: String, packageName: String) {
        HookEnv.setProcessName(processName)
        HookEnv.setHostAppPackageName(packageName)
    }

    fun initModulePath(path: String) {
        HookEnv.setModuleApkPath(path)
    }

    fun initContext(app: Application) {

        val packageManager = app.packageManager
        val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
        val appName = packageManager.getApplicationLabel(app.applicationInfo).toString()

        HookEnv.setHostAppContext(app)
        HookEnv.setApplication(app)
        HookEnv.setHostApkPath(app.applicationInfo.sourceDir)
        HookEnv.setAppName(appName)
        HookEnv.setVersionCode(PackageInfoCompat.getLongVersionCode(packageInfo))
        HookEnv.setVersionName(packageInfo.versionName ?: "unknown")

        ParasiticActivity.initForStubActivity(app)
        ResourcesUtils.injectResourcesToContext(app.resources)
    }

    fun initHooks(app: Application) {
        if (ProcUtil.isMain) {
            Log.i("""

                    android version: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    module version: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${ if (TCQTBuild.DEBUG) "Debug" else "Release" }
                    host version: ${PlatformTools.getHostVersion()}(${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}

                """.trimIndent())
        }

        ActionManager.runFirst(
            app,
            when {
                ProcUtil.isMain -> ActionProcess.MAIN
                ProcUtil.isMsf -> ActionProcess.MSF
                ProcUtil.isTool -> ActionProcess.TOOL
                ProcUtil.isOpenSdk -> ActionProcess.OPENSDK
                ProcUtil.isQzone -> ActionProcess.QZONE
                ProcUtil.isQQFav -> ActionProcess.QQFAV
                else -> ActionProcess.OTHER
            }
        )
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun injectClassLoader(loader: ClassLoader) {
        runCatching {
            val self = HookEnv::class.java.classLoader
            HybridClassLoader.setHostClassLoader(loader)
            HybridClassLoader.inject(self!!)
        }.onFailure {
            Log.e("injectClassLoader failed", it)
        }
    }
}
