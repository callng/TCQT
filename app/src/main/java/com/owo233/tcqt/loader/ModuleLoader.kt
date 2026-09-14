package com.owo233.tcqt.loader

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import com.owo233.tcqt.core.action.HookSteps
import com.owo233.tcqt.core.action.StartupScheduler
import com.owo233.tcqt.core.dexkit.DexKitCache
import com.owo233.tcqt.core.dexkit.DexKitFinder
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.hook.HookEngineManager
import com.owo233.tcqt.core.hook.HookFramework
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.Unhook
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.allConstructors
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.core.sync.SyncUtils
import com.owo233.tcqt.features.internal.pipeline.PipelineDecorators
import com.owo233.tcqt.features.message.RecallHeaderTip
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.loader.modern.ModernHookEngine
import com.owo233.tcqt.ui.parasitic.ParasiticActivity
import com.tencent.common.app.BaseApplicationImpl
import dalvik.system.BaseDexClassLoader
import io.fastkv.FastKV
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object ModuleLoader {

    private const val QFIX_PROXY_CLASS = "com.tencent.common.app.QFixApplicationImplProxy"
    private const val QFIX_IMPL_CLASS = "com.tencent.common.app.QFixApplicationImpl"
    private const val TINKER_LOADER_CLASS = "com.tencent.tinker.loader.TinkerLoader"
    private const val TINKER_TRY_LOAD_METHOD = "tryLoad"

    private val sLoaded = AtomicBoolean(false)

    private var isInit = AtomicBoolean(false)
    private var hasCapturedTinker = AtomicBoolean(false)

    fun initialize(
        hostClassLoader: ClassLoader,
        selfPath: String,
        packageName: String,
        processName: String
    ): Boolean {
        if (sLoaded.get()) return true

        if (!isHostClassLoaderReady(hostClassLoader)) {
            Log.e("host class not found!!!")
            return false
        }

        HookSteps.initModulePath(selfPath)
        HookSteps.initHandleLoadPackage(processName, packageName)

        if (!nextInit(hostClassLoader)) {
            return false
        }

        sLoaded.set(true)
        return true
    }

    private fun isHostClassLoaderReady(classLoader: ClassLoader): Boolean {
        return !(classLoader === this.javaClass.classLoader || classLoader !is BaseDexClassLoader) &&
                sequenceOf(
                    QFIX_PROXY_CLASS,
                    QFIX_IMPL_CLASS,
                ).any { className ->
                    try {
                        classLoader.loadClass(className)
                        true
                    } catch (_: ClassNotFoundException) {
                        false
                    }
                }
    }

    private fun nextInit(hostClassLoader: ClassLoader): Boolean {
        return listOf(
            QFIX_PROXY_CLASS,
            QFIX_IMPL_CLASS,
        ).any { className ->
            try {
                val clazz = hostClassLoader.loadClass(className)
                val method = clazz.getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java
                )
                hookQFixAttach(method)
                true
            } catch (_: ClassNotFoundException) {
                false
            } catch (th: Throwable) {
                Log.e("nextInit Failure: $className", th)
                false
            }
        }
    }

    private fun hookQFixAttach(attach: Method) {
        val constructorUnhooks = mutableListOf<Unhook>()

        attach.apply {
            hookBefore {
                tryDisableHotPatchEarly(it)

                BaseDexClassLoader::class.java.allConstructors().forEach { ctor ->
                    val unhook = ctor.hookAfter { param ->
                        val loader = param.thisObject as ClassLoader
                        val loaderStr = loader.toString()
                        if (loaderStr.contains(TCQTBuild.APP_ID)) return@hookAfter

                        if ((loaderStr.contains("com.tencent.") ||
                                    loaderStr.contains("TinkerClassLoader") ||
                                    loaderStr.contains("DelegateLastClassLoader"))
                            && !hasCapturedTinker.get()
                        ) {
                            hasCapturedTinker.set(true)
                            Log.d("捕获到热更新 ClassLoader： $loader")
                            doRealStartup(loader)
                        }
                    }
                    constructorUnhooks.add(unhook)
                }
            }

            hookAfter { param ->
                constructorUnhooks.forEach { it.unhook() }
                constructorUnhooks.clear()

                if (!hasCapturedTinker.get()) {
                    val context = param.args[0] as Context
                    doRealStartup(context.classLoader)
                }
            }
        }
    }

    @SuppressLint("SdCardPath")
    private fun tryDisableHotPatchEarly(param: MethodHookParam) {
        val context = param.args[0] as Context
        val appName = TCQTBuild.APP_NAME

        val oldPath = context.getExternalFilesDir(null)?.parentFile?.let {
            "${it.absolutePath}/$appName"
        } ?: "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/${context.packageName}/$appName"

        val newPath = (context.filesDir?.let { File(it, "5463306EE50FE3AA/$appName") }
            ?: File("/data/user/${Process.myUserHandle().hashCode()}/${context.packageName}/files/5463306EE50FE3AA/$appName"))
            .also { it.mkdirs() }
            .absolutePath

        // 将在后续的几个版本更新中移除迁移逻辑
        val settingPath = if (File(oldPath).exists()) {
            val kvOld = FastKV.Builder("$oldPath/global/setting", appName).build()
            val kvNew = FastKV.Builder("$newPath/global/setting", appName).build()
            kvNew.putBoolean("disable_hot_patch", kvOld.getBoolean("disable_hot_patch", false))
            oldPath
        } else newPath

        if (!FastKV.Builder("$settingPath/global/setting", appName).build()
                .getBoolean("disable_hot_patch", false)
        ) return

        try {
            val classLoader = param.thisObject.javaClass.classLoader!!
            val tryLoadMethod = classLoader
                .loadClass(TINKER_LOADER_CLASS)
                .getDeclaredMethod(
                    TINKER_TRY_LOAD_METHOD,
                    classLoader.loadClass("com.tencent.tinker.loader.app.TinkerApplication")
                )

            val stubException = object : UnsupportedOperationException("Fuck Tinker") {
                override fun fillInStackTrace() = this
            }

            tryLoadMethod.hookBefore {
                it.result = Intent().apply {
                    putExtra("intent_return_code", -3)
                    putExtra("intent_patch_exception", stubException)
                    putExtra("intent_patch_interpret_exception", stubException)
                }
            }
        } catch (th: Throwable) {
            Log.e("tryDisableHotPatchEarly failed", th)
        }
    }

    private fun doRealStartup(reClassLoader: ClassLoader) {
        if (isInit.get()) return
        HookEnv.setHostClassLoader(reClassLoader)
        HookSteps.injectClassLoader(reClassLoader)

        try {
            BaseApplicationImpl::class.java.getDeclaredMethod("onCreate").hookBefore { param ->
                if (isInit.compareAndSet(false, true)) {
                    installMainDispatcher()
                    val app = param.thisObject as Application
                    installHostBridge()
                    HookSteps.initContext(app)
                    System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

                    val cacheValid = DexKitCache.initCache()
                    val missingKeys = DexKitFinder.getMissingKeys()
                    val needDexKitFind = !cacheValid || missingKeys.isNotEmpty()

                    installPipelineDecorators()

                    // 只同步安装 CRITICAL，其余由 StartupScheduler 在 onCreate 返回后分批后台安装
                    val proc = HookSteps.resolveActionProcess()
                    val plan = HookSteps.initStartup(app, proc, missingKeys)
                    StartupScheduler.schedule(app, proc, plan, needDexKitFind)
                }
            }
        } catch (th: Throwable) {
            Log.e("doRealStartup Failure", th)
        }
    }

    private fun installMainDispatcher() {
        if (HookEngineManager.engine.frameworkName != HookFramework.ZYGISK) return
        if (!ProcUtil.isMain && !ProcUtil.isTool) return
        runCatching {
            kotlinx.coroutines.Dispatchers::class.java
                .getDeclaredMethod("getMain")
                .hookBefore { param ->
                    param.result = ModuleScope.mainDispatcher
                }
        }.onFailure {
            Log.e("hook Dispatchers.getMain failed", it)
        }
    }

    /**
     * 把上层能力注入 `core`。
     *
     * 必须在 [HookSteps.initContext] 之前调用：`initContext` 会触发
     * `HostBridge.notifyHostApplicationReady`，若此时还没有订阅者，
     * `ParasiticActivity` 就不会被初始化，寄生 Activity 直接失效。
     */
    private fun installHostBridge() {
        HostBridge.topActivityProvider = { QQInterfaces.topActivity }
        HostBridge.onHostApplicationReady { app ->
            ParasiticActivity.initForStubActivity(app)
        }
    }

    fun reload(state: Map<*, *>) {
        installMainDispatcher()
        HookSteps.initModulePath(state["moduleApkPath"] as String)
        HookSteps.initHandleLoadPackage(
            state["hostProcessName"] as String,
            state["hostAppPackageName"] as String
        )
        HookEnv.setHostClassLoader(state["hostClassLoader"] as ClassLoader)
        HookSteps.injectClassLoader(state["hostClassLoader"] as ClassLoader)
        installHostBridge()
        HookSteps.initContext(state["hostApplication"] as Application)

        System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

        if (HookEngineManager.engine is ModernHookEngine && ProcUtil.isMain) {
            SyncUtils.runOnUiThread {
                val topActivity = QQInterfaces.topActivity
                val activityName = topActivity.javaClass.name
                if (activityName.contains("SettingActivity")) {
                    topActivity.recreate()
                }
            }
        }

        val cacheValid = DexKitCache.initCache()
        val missingKeys = DexKitFinder.getMissingKeys()
        val needDexKitFind = !cacheValid || missingKeys.isNotEmpty()

        val app = state["hostApplication"] as Application
        installPipelineDecorators()
        val proc = HookSteps.resolveActionProcess()
        val plan = HookSteps.initStartup(app, proc, missingKeys)
        StartupScheduler.schedule(app, proc, plan, needDexKitFind)
    }

    /**
     * 登记非注册的管线装饰器。
     *
     * 注册 Action 的装饰器由 `PipelineDecorators` 从 `ActionRegistry` 自动发现；
     * 但像 `RecallHeaderTip` 这种"某功能的渲染器、自己没有功能开关"的普通类没有
     * 注册项，必须显式登记一次。两条启动路径都在 `HookSteps.initStartup` 之前
     * 调用本方法，因此时机确定，早于任何管线的 `install()`。
     */
    private fun installPipelineDecorators() {
        PipelineDecorators.register(RecallHeaderTip())
    }
}
