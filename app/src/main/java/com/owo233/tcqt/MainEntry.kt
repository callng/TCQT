package com.owo233.tcqt

import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import android.os.Build
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.initHostInfo
import com.owo233.tcqt.hooks.base.moduleLoadInit
import com.owo233.tcqt.hooks.base.modulePath
import com.owo233.tcqt.hooks.base.moduleRes
import com.owo233.tcqt.hooks.base.resInjection
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isStatic
import com.owo233.tcqt.utils.paramCount
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainEntry: IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (HostTypeEnum.contain(lpparam.packageName)) {
            doHook(lpparam.classLoader)
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    private fun doHook(classLoader: ClassLoader) {
        val startup = afterHook(51) { param ->
            param.thisObject.javaClass.classLoader!!.also { loader ->
                XpClassLoader.ctxClassLoader = loader

                val baseApplicationImpl = "com.tencent.common.app.BaseApplicationImpl"
                val app = loader.loadClass(baseApplicationImpl)
                    .declaredFields.first { it.type.name == baseApplicationImpl }
                    .apply { isAccessible = true }
                    .get(null) as Context

                app.let(::execStartupInit)
            }
        }

        hookStartup(classLoader, startup)
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookStartup(classLoader: ClassLoader, startup: XC_MethodHook) = runCatching {
        val taskClass = classLoader.loadClass("com.tencent.mobileqq.startup.task.config.b")

        val taskMapField = taskClass.declaredFields.firstOrNull {
            Map::class.java.isAssignableFrom(it.type) && it.isStatic
        }?.apply { isAccessible = true } ?: error("No static Map field found in $taskClass")

        val taskMap = taskMapField.get(null) as? Map<String, Class<*>>
            ?: error("Static field ${taskMapField.name} is not a valid Map or is empty")

        val targetEntry = taskMap.entries.firstOrNull {
            it.key.contains("LoadDexTask", ignoreCase = true)
        } ?: error("No LoadDexTask found in taskMap of $taskClass")

        val targetMethod = targetEntry.value.declaredMethods.firstOrNull { m ->
            m.paramCount == 1 && m.parameterTypes[0] == Context::class.java
        } ?: error("No matching method in ${targetEntry.value} for LoadDexTask")

        targetMethod.hookMethod(startup)
    }.onFailure {
        Log.e("hookStartup failed: ${it.message}", it)
    }

    private fun execStartupInit(ctx: Context) {
        initHostInfo(ctx as Application)

        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XpClassLoader.hostClassLoader = classLoader

        if (XpClassLoader.injectClassloader()) {
            if (ProcUtil.isMain) {
                Log.i("""

                    android version: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    module version: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${ if (TCQTBuild.DEBUG) "Debug" else "Release" }
                    host version: ${PlatformTools.getHostVersion()}(${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}

                """.trimIndent())
            }

            if (ProcUtil.isMain) {
                resInjection(ctx)
            }

            ActionManager.runFirst(ctx, when {
                ProcUtil.isMain -> ActionProcess.MAIN
                ProcUtil.isMsf -> ActionProcess.MSF
                ProcUtil.isTool -> ActionProcess.TOOL
                ProcUtil.isOpenSdk -> ActionProcess.OPENSDK
                else -> ActionProcess.OTHER
            })

            moduleLoadInit = true
        } else {
            Log.e("XpClassLoader inject failed.")
        }
    }
}
