package com.owo233.tcqt

import android.app.Application
import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.PACKAGE_NAME_QQ
import com.owo233.tcqt.utils.PACKAGE_NAME_TIM
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.initHostInfo
import com.owo233.tcqt.utils.logE
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Modifier

class MainEntry: IXposedHookLoadPackage {
    private var firstStageInit = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == PACKAGE_NAME_QQ || lpparam.packageName == PACKAGE_NAME_TIM) {
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

        if (injectClassloader(MainEntry::class.java.classLoader!!)) {
            if ("114514" != System.getProperty("TCQT_flag")) {
                System.setProperty("TCQT_flag", "114514")
            } else return

            // logI(msg = "PName = " + MobileQQ.getMobileQQ().qqProcessName)

            secStaticStageInited = true

            ActionManager.runFirst(ctx, when {
                PlatformTools.isMainProcess() -> ActionProcess.MAIN
                PlatformTools.isMsfProcess() -> ActionProcess.MSF
                PlatformTools.isToolProcess() -> ActionProcess.TOOL
                PlatformTools.isOpenSdkProcess() -> ActionProcess.OPENSDK
                else -> ActionProcess.OTHER
            })
        }
    }

    private fun injectClassloader(moduleLoader: ClassLoader): Boolean {
        if (runCatching {
                moduleLoader.loadClass("mqq.app.MobileQQ")
            }.isSuccess) {
            // logI(msg = "ModuleClassloader already injected.")
            return true
        }

        val parent = moduleLoader.parent
        val field = ClassLoader::class.java.declaredFields
            .first { it.name == "parent" }
        field.isAccessible =true

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
        }.onSuccess {
            // logI(msg = "Classloader inject successfully.")
        }.isSuccess
    }

    companion object {
        @JvmStatic
        var secStaticStageInited = false
    }
}
