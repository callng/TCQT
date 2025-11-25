package com.owo233.tcqt

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isStatic
import com.owo233.tcqt.utils.paramCount
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

internal object HookSteps {

    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setCurrentHostAppPackageName(loadPackageParam.packageName)
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        HookEnv.setModuleApkPath(startupParam.modulePath)
    }

    fun initLoad(lpparam: XC_LoadPackage.LoadPackageParam) {
        val startup = afterHook(51) { param ->
            param.thisObject.javaClass.classLoader!!.also { loader ->
                val impl = "com.tencent.common.app.BaseApplicationImpl"
                val app = loader.loadClass(impl)
                    .declaredFields
                    .first {  it.type.name == impl }
                    .apply { isAccessible = true }
                    .get(null) as Application

                XpClassLoader.init(loader, app.baseContext.classLoader)
                initContext(app)
                initHooks(app)
            }
        }
        hookStartup(lpparam.classLoader, startup)
    }

    private fun initContext(app: Application) {
        val context = app.baseContext
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
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookStartup(loader: ClassLoader, startup: XC_MethodHook) {
        (loader.loadClass("com.tencent.mobileqq.startup.task.config.b")
            .declaredFields
            .first { Map::class.java.isAssignableFrom(it.type) && it.isStatic }
            .apply { isAccessible = true }
            .get(null) as Map<String, Class<*>>)
            .entries
            .first { it.key.contains("LoadDexTask", ignoreCase = true) }
            .value
            .declaredMethods
            .first { it.paramCount == 1 && it.parameterTypes[0] == Context::class.java }
            .hookMethod(startup)
    }

    private fun initHooks(app: Application) {
        injectClassLoader()
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
                else -> ActionProcess.OTHER
            }
        )
    }

    private fun injectClassLoader() {
        val loader = XpClassLoader.INSTANCE
        val self = HookEntry::class.java.classLoader!!
        try {
            val fParent = ClassLoader::class.java.getDeclaredField("parent")
            fParent.isAccessible = true
            fParent.set(self, loader)
        } catch (e: Exception) {
            android.util.Log.e("TCQT", "injectClassLoader failed", e)
        }
    }
}
