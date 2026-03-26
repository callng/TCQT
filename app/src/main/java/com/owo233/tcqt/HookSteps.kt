package com.owo233.tcqt

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.hooks.base.HybridClassLoader
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.lifecycle.ParasiticActivity
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findMethod
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

internal object HookSteps {

    lateinit var hostApp: Application
    val hostInit get() = ::hostApp.isInitialized

    /** 兼容双入口：记录模块 APK 路径 */
    var moduleApkPath: String = ""
        private set

    /** 传统 Xposed 入口使用的初始化方法 */
    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setHostAppPackageName(loadPackageParam.packageName)
    }

    /** libxposed 入口使用的初始化方法 */
    fun initHandleLoadPackageCompat(processName: String, packageName: String, classLoader: ClassLoader) {
        HookEnv.setProcessName(processName)
        HookEnv.setHostAppPackageName(packageName)
        // libxposed 下需要在此处注入 ClassLoader（传统入口在 initZygote 中获取 modulePath）
        if (moduleApkPath.isEmpty()) {
            moduleApkPath = com.owo233.tcqt.xposed.HookerBridgeManager.bridge.modulePath
        }
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        moduleApkPath = startupParam.modulePath
        HookEnv.setModuleApkPath(moduleApkPath)
    }

    fun initLoad() {
        Instrumentation::class.java.findMethod {
            name = "callApplicationOnCreate"
            paramTypes(Application::class.java)
        }.hookAfterMethod { param ->
            val application = param.args[0] as Application
            val context = application.baseContext ?: application
            if (hostInit.not()) {
                hostApp = application
                injectClassLoader(context.classLoader)
                initContext(application, context.classLoader)
                Log.i("pName: ${ProcUtil.procName}, pPid: ${ProcUtil.mPid}")
                initHooks(application)
            }
        }
    }

    private fun initContext(app: Application, loader: ClassLoader) {
        val context = app.baseContext ?: run {
            Log.e("initContext: baseContext is null, using app as fallback")
            app
        }

        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        val appName = packageManager.getApplicationLabel(context.applicationInfo).toString()

        HookEnv.setHostAppContext(context)
        HookEnv.setApplication(app)
        HookEnv.setHostApkPath(context.applicationInfo.sourceDir)
        HookEnv.setAppName(appName)
        HookEnv.setVersionCode(PackageInfoCompat.getLongVersionCode(packageInfo))
        HookEnv.setVersionName(packageInfo.versionName ?: "unknown")
        HookEnv.setHostClassLoader(loader)

        // 在 libxposed 入口时，moduleApkPath 可能尚未设置到 HookEnv
        if (moduleApkPath.isNotEmpty()) {
            HookEnv.setModuleApkPath(moduleApkPath)
        }

        ParasiticActivity.initForStubActivity(context)
        ResourcesUtils.injectResourcesToContext(context.resources)
    }

    private fun initHooks(app: Application) {
        if (ProcUtil.isMain) {
            Log.i("""

                    android version: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    module version: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${ if (TCQTBuild.DEBUG) "Debug" else "Release" }
                    host version: ${PlatformTools.getHostVersion()}(${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}

                """.trimIndent())
        }
        ActionManager.runFirst(
            app.baseContext ?: app,
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
            val self = HookEntry::class.java.classLoader
            HybridClassLoader.setHostClassLoader(loader)
            HybridClassLoader.inject(self!!)
        }.onFailure {
            Log.e("injectClassLoader failed", it)
        }
    }
}
