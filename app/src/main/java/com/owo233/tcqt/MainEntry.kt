package com.owo233.tcqt

import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.utils.logI
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import mqq.app.MobileQQ
import java.lang.reflect.Modifier

class MainEntry: IXposedHookLoadPackage {
    private var firstStageInit = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == PACKAGE_NAME_QQ || lpparam.packageName == PACKAGE_NAME_TIM) {
            entryQQ(lpparam.classLoader)
        }
    }

    private fun entryQQ(classLoader: ClassLoader) {
        val startup = afterHook(51) { param ->
            try {
                val loader = param.thisObject.javaClass.classLoader!!
                XpClassLoader.ctxClassLoader = loader

                val clz = loader.loadClass("com.tencent.common.app.BaseApplicationImpl")
                val field = clz.declaredFields.first {
                    it.type == clz
                }

                val app: Context? = field.get(null) as? Context
                app?.let {
                    execStartupInit(it)
                }
            } catch (e: Throwable) {
                logE(msg = "startup 异常", cause = e)
            }
        }

        runCatching {
            val clz = FuzzyClassKit.findClassesByField(classLoader, "com.tencent.mobileqq.startup.task.config") { _, field ->
                (field.type == HashMap::class.java) && Modifier.isStatic(field.modifiers)
            }.firstOrNull()

            if (clz == null) {
                logE(msg = "startup: 找不到与之匹配的class,模块初始化失败")
                return
            }

            val field = clz.declaredFields.firstOrNull {
                it.type == HashMap::class.java && Modifier.isStatic(it.modifiers)
            }

            if (field == null) {
                logE(msg = "startup: 找不到与之匹配的field,模块初始化失败")
                return
            }

            if (!field.isAccessible) field.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as? HashMap<String, Class<*>>
            if (map == null) {
                logE(msg = "startup: field无法转换为map,模块初始化失败")
                return
            }

            for ((key, clazz) in map) {
                if (key.contains("LoadDex", ignoreCase = true)) {
                    clazz.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java
                    }?.hookMethod(startup)

                    break
                }
            }

            firstStageInit = true
        }.onFailure {
            logE(msg = "entryQQ 异常", cause = it)
        }
    }

    private fun execStartupInit(ctx: Context) {
        if (secStaticStageInited) return

        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XpClassLoader.hostClassLoader = classLoader

        if (injectClassloader(MainEntry::class.java.classLoader!!)) {
            if ("114514" != System.getProperty("TCQT_flag")) {
                System.setProperty("TCQT_flag", "114514")
            } else return

            logI(msg = "PName = " + MobileQQ.getMobileQQ().qqProcessName)

            secStaticStageInited = true

            ActionManager.runFirst(ctx, when {
                PlatformTools.isMainProcess() -> ActionProcess.MAIN
                PlatformTools.isMsfProcess() -> ActionProcess.MSF
                PlatformTools.isToolProcess() -> ActionProcess.TOOL
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
        @JvmStatic var secStaticStageInited = false

        internal const val PACKAGE_NAME_QQ = "com.tencent.mobileqq"
        internal const val PACKAGE_NAME_TIM = "com.tencent.tim"
    }
}
