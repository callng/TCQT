package com.owo233.tcqt

import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import android.os.Build
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.initHostInfo
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.hooks.base.moduleLoadInit
import com.owo233.tcqt.hooks.base.modulePath
import com.owo233.tcqt.hooks.base.moduleRes
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.isStatic
import com.owo233.tcqt.utils.logI
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
    private fun hookStartup(classLoader: ClassLoader, startup: XC_MethodHook) {
        val kTaskClz = classLoader.loadClass("com.tencent.mobileqq.startup.task.config.b")
            ?: throw ClassNotFoundException("com.tencent.mobileqq.startup.task.config.b does not exist.")
        val kITaskClz = classLoader.loadClass("com.tencent.qqnt.startup.task.d")
            ?: throw ClassNotFoundException("com.tencent.qqnt.startup.task.d does not exist.")
        if (!kITaskClz.isAssignableFrom(kTaskClz)) {
            throw AssertionError("$kITaskClz is not assignable from $kTaskClz")
        }

        val taskMapField = kTaskClz.declaredFields.firstOrNull { field ->
            field.type == HashMap::class.java && field.isStatic
        }?.apply { isAccessible = true } ?: throw NoSuchFieldException("No static field taskMap in $kTaskClz")
        val taskMap = taskMapField.get(null) as? Map<String, Class<*>>
            ?: throw AssertionError("$taskMapField is not a Map")

        taskMap.also { map ->
            map.forEach { (key, clazz) ->
                if (key.contains("LoadDexTask", ignoreCase = true)) {
                    clazz.declaredMethods.firstOrNull { method ->
                        method.paramCount == 1 && method.parameterTypes[0] == Context::class.java
                    }?.hookMethod(startup) ?: throw NoSuchMethodException("$clazz No matching methods found")
                }
            }
        }
    }

    private fun execStartupInit(ctx: Context) {
        if (secStaticStageInited) return

        initHostInfo(ctx as Application)

        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XpClassLoader.hostClassLoader = classLoader

        if (XpClassLoader.injectClassloader()) {
            if ("114514" != System.getProperty("TCQT_flag")) {
                System.setProperty("TCQT_flag", "114514")
            } else return

            secStaticStageInited = true

            if (ProcUtil.isMain) {
                logI(msg = """


                    android version: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    module version: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${ if (TCQTBuild.DEBUG) "Debug" else "Release" }
                    host version: ${PlatformTools.getHostVersion()}(${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}


                """.trimIndent())
            }

            ActionManager.runFirst(ctx, when {
                ProcUtil.isMain -> ActionProcess.MAIN
                ProcUtil.isMsf -> ActionProcess.MSF
                ProcUtil.isTool -> ActionProcess.TOOL
                ProcUtil.isOpenSdk -> ActionProcess.OPENSDK
                else -> ActionProcess.OTHER
            })

            moduleLoadInit = true
        }
    }

    companion object {
        @JvmStatic
        var secStaticStageInited = false
    }
}
