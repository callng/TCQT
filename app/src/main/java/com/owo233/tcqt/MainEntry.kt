package com.owo233.tcqt

import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import android.os.Build
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.hooks.base.initHostInfo
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.utils.field
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.hooks.base.moduleClassLoader
import com.owo233.tcqt.hooks.base.moduleLoadInit
import com.owo233.tcqt.hooks.base.modulePath
import com.owo233.tcqt.hooks.base.moduleRes
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.injectRes
import com.owo233.tcqt.utils.logI
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Modifier

class MainEntry: IXposedHookLoadPackage, IXposedHookZygoteInit {
    private var firstStageInit = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (HostTypeEnum.contain(lpparam.packageName)) {
            entryQQ(lpparam.classLoader)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun entryQQ(classLoader: ClassLoader) {
        val startup = afterHook(51) {
            runCatching {
                it.thisObject.javaClass.classLoader?.also { loader ->
                    XpClassLoader.ctxClassLoader = loader

                    val app = loader.loadClass("com.tencent.common.app.BaseApplicationImpl")
                        .declaredFields.first { f -> f.type.name == "com.tencent.common.app.BaseApplicationImpl" }
                        .apply { isAccessible = true }
                        .get(null) as? Context

                    app?.let(::execStartupInit)
                }
            }.onFailure { e ->
                logE(msg = "startup 异常", cause = e)
            }
        }

        runCatching {
            val map = FuzzyClassKit.findClassesByField(classLoader, "com.tencent.mobileqq.startup.task.config") { _, f ->
                f.type == HashMap::class.java && Modifier.isStatic(f.modifiers)
            }.firstNotNullOfOrNull { clz ->
                clz.declaredFields.firstOrNull {
                    it.type == HashMap::class.java && Modifier.isStatic(it.modifiers)
                }?.apply { isAccessible = true }?.get(null) as? HashMap<String, Class<*>>
            } ?: return@runCatching logE(msg = "startup: 找不到静态 HashMap 字段")

            map.entries.firstOrNull { it.key.contains("LoadDex", ignoreCase = true) }?.value
                ?.declaredMethods?.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java
                }?.hookMethod(startup)

            firstStageInit = true
        }.onFailure {
            logE(msg = "entryQQ 异常", cause = it)
        }
    }

    private fun execStartupInit(ctx: Context) {
        if (secStaticStageInited) return

        initHostInfo(ctx as Application)

        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XpClassLoader.hostClassLoader = classLoader

        if (injectClassloader()) {
            if ("114514" != System.getProperty("TCQT_flag")) {
                System.setProperty("TCQT_flag", "114514")
            } else return

            secStaticStageInited = true

            if (ProcUtil.isMain) {
                injectRes()

                logI(msg = """


                    android version: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    module version: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${ if (TCQTBuild.DEBUG) "Debug" else "Release" }
                    host version: ${PlatformTools.getQQVersion()}(${PlatformTools.getQQVersionCode()}) ${PlatformTools.getQQChannel()}


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

    private fun injectClassloader(): Boolean {
        val moduleLoader = moduleClassLoader
        if (runCatching { moduleLoader.loadClass("mqq.app.MobileQQ") }.isSuccess) return true

        val parent = moduleLoader.parent
        val field = ClassLoader::class.java.field("parent")!!

        field.set(XpClassLoader, parent)

        if (XpClassLoader.load("mqq.app.MobileQQ") == null) {
            logE(msg = "XpClassLoader inject failed.")
            return false
        }

        field.set(moduleLoader, XpClassLoader)

        return runCatching {
            Class.forName("mqq.app.MobileQQ")
        }.onFailure {
            logE(msg = "Classloader inject failed.")
        }.isSuccess
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    companion object {
        @JvmStatic
        var secStaticStageInited = false
    }
}
