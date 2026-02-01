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

    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setHostAppPackageName(loadPackageParam.packageName)
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        HookEnv.setModuleApkPath(startupParam.modulePath)
    }

    fun initLoad() {
        Instrumentation::class.java.findMethod {
            name = "callApplicationOnCreate"
            paramTypes(Application::class.java)
        }.hookAfterMethod { param ->
            val application = param.args[0] as Application
            val context = application.baseContext
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

        runCatching {
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

            ResourcesUtils.injectResourcesToContext(context.resources)
        }.onFailure {
            Log.e("initContext: Failed to initialize context", it)
        }
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
