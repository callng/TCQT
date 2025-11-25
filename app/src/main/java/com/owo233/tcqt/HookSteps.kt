package com.owo233.tcqt

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.hooks.base.XpClassLoader
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.field
import com.owo233.tcqt.utils.fieldValue
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

internal object HookSteps {

    lateinit var hostApp: Application
    val hostInit get() = ::hostApp.isInitialized

    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setCurrentHostAppPackageName(loadPackageParam.packageName)
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        HookEnv.setModuleApkPath(startupParam.modulePath)
    }

    fun initLoad(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                "com.tencent.common.app.BaseApplicationImpl",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (hostInit) return
                        hostApp = param.thisObject as Application
                        initContext(hostApp)
                        injectClassLoader()
                        initHooks(hostApp)
                    }
                })
        }.onFailure {
            Log.e("hookStartup failed", it)
        }
    }

    private fun initContext(app: Application) {
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
            HookEnv.setHostClassLoader(context.classLoader)

            ResourcesUtils.injectResourcesToContext(context, HookEnv.moduleApkPath)
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
                else -> ActionProcess.OTHER
            }
        )
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectClassLoader() {
        val fParent = ClassLoader::class.java.field("parent")!!
        val mClassloader = HookEnv.moduleClassLoader
        val parentClassloader = mClassloader.fieldValue("parent", true) as ClassLoader
        runCatching {
            if (XpClassLoader::class.java != parentClassloader::class.java) {
                fParent.set(mClassloader, XpClassLoader(HookEnv.hostClassLoader, parentClassloader))
            }
        }.onFailure {
            Log.e("injectClassLoader: Failed to inject classloader", it)
        }
    }
}
