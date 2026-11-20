package com.owo233.tcqt.loader

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentProvider
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import com.owo233.tcqt.core.action.ActionRegistry
import com.owo233.tcqt.core.action.HookSteps
import com.owo233.tcqt.core.action.StartupScheduler
import com.owo233.tcqt.core.action.StartupTrace
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.dexkit.DexKitCache
import com.owo233.tcqt.core.dexkit.DexKitFinder
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.HostServicesProvider
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
import com.owo233.tcqt.host.QQHostServices
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.loader.modern.ModernHookEngine
import com.owo233.tcqt.loader.validation.RuntimeValidation
import com.owo233.tcqt.ui.component.DexKitScanDialogHost
import com.owo233.tcqt.ui.parasitic.ActivityProxyManager
import com.owo233.tcqt.ui.settings.SettingsEntryWarmup
import com.tencent.common.app.BaseApplicationImpl
import dalvik.system.BaseDexClassLoader
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
        // 取证钩子必须在宿主 attachBaseContext / ContentProvider 安装之前挂上，
        // initialize 由 LoadedApk.instantiateClassLoader 触发，早于二者。
        installBootstrapTraceHooks()

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
                            && hasCapturedTinker.compareAndSet(false, true)
                        ) {
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
        val settingPath = if (File(oldPath).exists()) oldPath else newPath

        if (SettingsStore(File("$settingPath/global/setting"), appName)["disable_hot_patch"] != true) return

        try {
            val classLoader = requireNotNull(param.thisObject).javaClass.classLoader!!
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

    /**
     * 只记录、不改行为：把 `Application.attachBaseContext` 与 `ContentProvider.attachInfo`
     * 的名称和耗时写进 [StartupTrace]。用于定位 module-entry → onCreate 之间的长耗时
     * （r756 出现过启动页卡 66s，而看门狗当时还没装上，stall.log 抓不到）。
     */
    private fun installBootstrapTraceHooks() {
        runCatching {
            val attachStart = java.util.concurrent.atomic.AtomicLong(0L)
            Application::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply {
                    hookBefore { attachStart.set(SystemClock.elapsedRealtime()) }
                    hookAfter {
                        StartupTrace.mark(
                            "attachBaseContext=${SystemClock.elapsedRealtime() - attachStart.get()}ms"
                        )
                    }
                }
        }.onFailure { Log.e("bootstrap trace: attachBaseContext hook failed", it) }

        runCatching {
            val starts = java.util.concurrent.ConcurrentHashMap<Any, Long>()
            ContentProvider::class.java
                .getDeclaredMethod("attachInfo", Context::class.java, ProviderInfo::class.java)
                .apply {
                    hookBefore { param ->
                        val provider = param.thisObject ?: return@hookBefore
                        starts[provider] = SystemClock.elapsedRealtime()
                    }
                    hookAfter { param ->
                        val startedAt = starts.remove(param.thisObject) ?: return@hookAfter
                        val info = param.args.getOrNull(1) as? ProviderInfo
                        val label = info?.name?.substringAfterLast('.') ?: "unknown"
                        StartupTrace.mark(
                            "provider:$label=${SystemClock.elapsedRealtime() - startedAt}ms"
                        )
                    }
                }
        }.onFailure { Log.e("bootstrap trace: provider attach hook failed", it) }
    }

    private fun doRealStartup(reClassLoader: ClassLoader) {
        if (isInit.get()) return
        StartupTrace.begin("module-entry")
        HookEnv.setHostClassLoader(reClassLoader)
        HookSteps.injectClassLoader(reClassLoader)
        // r757：看门狗必须在 onCreate 之前就装上。r756 只在 onCreate Before 里 install，
        // 导致 16:47 那次 module-entry→onCreate-before 的 66s 卡顿完全抓不到。
        // install() 自带 installed 幂等保护，onCreate 处保留的调用只是兜底。
        runCatching { com.owo233.tcqt.core.diag.StallWatchdog.install() }
            .onFailure { Log.e("StallWatchdog early install failed", it) }

        try {
            val startupAfterCreate = com.owo233.tcqt.core.action.StartupAfterCreate<Application>()
            val onCreate = BaseApplicationImpl::class.java.getDeclaredMethod("onCreate")
            onCreate.hookBefore { param ->
                if (isInit.compareAndSet(false, true)) {
                    StartupTrace.mark("onCreate-before")
                    StartupTrace.measure("main-dispatcher") { installMainDispatcher() }
                    val app = param.thisObject as Application
                    // 补丁加载尝试可能失败并回退。以 Application 实际采用的 loader 为准。
                    val activeClassLoader = app.classLoader
                    HookEnv.setHostClassLoader(activeClassLoader)
                    HookSteps.injectClassLoader(activeClassLoader)
                    StartupTrace.measure("host-bridge") { installHostBridge() }
                    StartupTrace.measure("init-context") { HookSteps.initContext(app) }
                    // 主线程卡顿看门狗：只挂 MAIN/MSF，安装本身只创建线程 + 投递一次心跳。
                    StartupTrace.measure("watchdog") { com.owo233.tcqt.core.diag.StallWatchdog.install() }
                    StartupTrace.measure("validation") { RuntimeValidation.install(app) }
                    System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

                    val cacheValid = StartupTrace.measure("dexkit-cache") {
                        DexKitCache.initCache(HostCodeMode.identify(activeClassLoader))
                    }
                    // 全量枚举 DexKit 任务会构造每个功能的查询对象；只算一次并复用，
                    // 避免 getMissingKeys()/needsFind() 各自再跑一遍。
                    val dexKit = StartupTrace.measure("dexkit-keys") { DexKitFinder.startupKeys() }
                    val dexKitKeys = dexKit.keys
                    val missingKeys = DexKitFinder.getMissingKeys(dexKitKeys)
                    StartupTrace.mark("dexkit-tasks=${dexKit.taskCount} keys=${dexKitKeys.size} missing=${missingKeys.size}")
                    val proc = HookSteps.resolveActionProcess()
                    // 同一补丁跨重启复用缓存；代码指纹变化由 initCache 使缓存失效。
                    val needDexKitFind = !cacheValid || DexKitFinder.needsFind(dexKitKeys)

                    StartupTrace.measure("decorators") { installPipelineDecorators() }

                    // 只同步安装 CRITICAL，其余由 StartupScheduler 在 onCreate 返回后分批后台安装
                    val plan = StartupTrace.measure("plan+critical") {
                        HookSteps.initStartup(app, proc, missingKeys)
                    }
                    StartupTrace.mark(
                        "onCreate-before-done critical=${plan.critical.size}" +
                                " early=${plan.early.size} deferred=${plan.deferred.size}" +
                                " background=${plan.background.size}" +
                                " plan=${ActionRegistry.lastPlanCostMs}ms" +
                                " dexkitFind=$needDexKitFind"
                    )
                    startupAfterCreate.prepare(app) {
                        StartupScheduler.schedule(
                            app, proc, plan, needDexKitFind,
                            dexKitClassLoader = activeClassLoader,
                        )
                    }
                }
            }
            onCreate.hookAfter { param ->
                StartupTrace.mark("onCreate-returned")
                startupAfterCreate.complete(param.thisObject as Application, param.throwable)
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
     * `ActivityProxyManager` 就不会被初始化，寄生 Activity 直接失效。
     */
    private fun installHostBridge() {
        HostServicesProvider.services = QQHostServices
        HostBridge.topActivityProvider = { QQInterfaces.topActivity }
        HostBridge.prepareSettings = SettingsEntryWarmup::schedule
        HostBridge.showDexKitScan = { state, onClosed ->
            DexKitScanDialogHost(
                HookEnv.application, state, onClosed,
            ).attach()
        }
        HostBridge.onHostApplicationReady {
            ActivityProxyManager.install()
        }
    }

    fun reload(state: Map<*, *>) {
        StartupTrace.begin("module-reload")
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
        com.owo233.tcqt.core.diag.StallWatchdog.install()
        RuntimeValidation.install(state["hostApplication"] as Application)

        System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

        if (HookEngineManager.engine is ModernHookEngine && ProcUtil.isMain) {
            SyncUtils.runOnUiThread {
                val topActivity = QQInterfaces.topActivity ?: return@runOnUiThread
                val activityName = topActivity.javaClass.name
                if (activityName.contains("SettingActivity")) {
                    topActivity.recreate()
                }
            }
        }

        // 始终根据正在运行的 loader 判断，不让旧模块保存的布尔值覆盖真实代码来源。
        val cacheValid = DexKitCache.initCache(HostCodeMode.identify(HookEnv.hostClassLoader))
        val dexKitKeys = DexKitFinder.startupKeys().keys
        val missingKeys = DexKitFinder.getMissingKeys(dexKitKeys)
        val proc = HookSteps.resolveActionProcess()
        val needDexKitFind = !cacheValid || DexKitFinder.needsFind(dexKitKeys)

        val app = state["hostApplication"] as Application
        installPipelineDecorators()
        val plan = HookSteps.initStartup(app, proc, missingKeys)
        StartupScheduler.schedule(
            app, proc, plan, needDexKitFind,
        )
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
