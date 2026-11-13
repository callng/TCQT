package com.owo233.tcqt.features.misc

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.core.log.LogAndroid
import com.owo233.tcqt.core.reflect.callOriginal
import com.owo233.tcqt.core.reflect.findMethods
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.FarmAccountHost
import com.owo233.tcqt.host.service.FarmAccountProtocol
import com.owo233.tcqt.host.service.FarmGameProcess
import com.owo233.tcqt.host.service.receiver
import com.owo233.tcqt.host.service.remoteReceiver
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID

@RegisterAction(processes = [ActionProcess.ALL], priority = ActionPriority.CRITICAL)
object FarmAccountSelector : Feature(
    key = "farm_account_selector",
    name = "农场启动时选择账号",
    desc = "打开 QQ 经典农场时选择已登录的账号，或使用自定义 js_code；可复制各账号的新登录码。进入前会关闭旧农场，无需切换 QQ 主账号。",
    requires = Requires(minQQVersion = QQVersion.QQ_9_3_0, host = Requires.Host.QQOnly, ntOnly = true),
) {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var pendingDialog: FarmAccountDialog? = null
    private val activities = CopyOnWriteArrayList<Activity>()
    @Volatile private var session: FarmSession? = null

    override fun install() {
        val main = HookEnv.isMainProcess()
        val mini = HookEnv.processName.substringAfter(':', "").startsWith("mini")
        if (!main && !mini) return
        LogAndroid.d("[FarmAccount] Installing Activity-bound account selection")
        if (main) hostApp.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) = Unit
            override fun onActivityDestroyed(a: Activity) { activities.remove(a) }
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) {
                if (!a.isTemporaryLauncher()) {
                    // Keep the last real foreground page across the short-lived mini-app loader.
                    activities.remove(a)
                    activities.add(a)
                }
            }
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
        })
        if (mini) installMiniLifecycle()
        // Register only framework hooks before Application.onCreate. QQ's SDK loader needs
        // the completed host Application; resolve its types before the first Activity instead.
        val installed = AtomicBoolean()
        Instrumentation::class.java.findMethods { name = "callActivityOnCreate" }.forEach { method ->
            method.hookBefore {
                if (!installed.compareAndSet(false, true)) return@hookBefore
                try {
                    if (main) installLauncher() else installMiniLogin()
                } catch (e: Exception) {
                    ActionErrorStore.report(key, "农场 SDK 初始化", e)
                }
            }
        }
    }

    private fun installLauncher() {
        val infoClass = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.MiniAppInfo")
        val loginInfoClass = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.LoginInfo")
        val setAccount = loginInfoClass.getMethod("setAccount", String::class.java)
        val setNickname = loginInfoClass.getMethod("setNickName", String::class.java)
        val host = FarmAccountHost(hostApp)
        val processes = FarmGameProcess()
        val launcher = loadOrThrow("com.tencent.qqmini.sdk.server.LaunchManagerService").getMethod(
            "startMiniApp", Activity::class.java, infoClass, Bundle::class.java, ResultReceiver::class.java,
        )
        host.installRoutingHooks()

        Instrumentation::class.java.findMethods { name = "execStartActivity" }.forEach { method ->
            val index = method.parameterTypes.indexOf(Intent::class.java)
            if (index < 0) return@forEach
            method.hookBefore { param ->
                val intent = param.args[index] as? Intent ?: return@hookBefore
                if (!intent.hasExtra(FarmAccountProtocol.EXTRA_ACCOUNT)) return@hookBefore
                intent.setExtrasClassLoader(HookEnv.hostClassLoader)
                if (appId(intent.appInfo()) != FarmAccountProtocol.APP_ID) return@hookBefore
                @Suppress("DEPRECATION")
                val login = intent.getParcelableExtra<android.os.Parcelable>("KEY_LOGININFO") ?: return@hookBefore
                setAccount.invoke(login, intent.getStringExtra(FarmAccountProtocol.EXTRA_ACCOUNT))
                setNickname.invoke(login, intent.getStringExtra(FarmAccountProtocol.EXTRA_NICKNAME))
            }
        }

        launcher.hookBefore { param ->
            val info = param.args[1] ?: return@hookBefore
            if (appId(info) != FarmAccountProtocol.APP_ID) return@hookBefore
            LogAndroid.d("[FarmAccount] Intercepted farm launch")
            val requested = param.args[0] as? Activity
            // QQ's PreloadingFragment finishes as soon as the SDK call returns. A deferred
            // picker belongs to the foreground page, including channel/fragment Activities.
            val activity = activities.lastOrNull { it.canHostPicker() && it.hasWindowFocus() }
                ?: activities.lastOrNull { it.canHostPicker() }
                ?: requested?.takeIf { it.canHostPicker() }
                ?: QQInterfaces.topActivity?.takeIf { it.canHostPicker() }
                ?: return@hookBefore
            val receiver = param.args[3] as? ResultReceiver
            val target = param.thisObject ?: return@hookBefore
            val args = param.args.copyOf()
            args[0] = activity
            param.result = null
            mainHandler.post {
                LogAndroid.d("[FarmAccount] Picker host=${activity.javaClass.name}, finishing=${activity.isFinishing}, destroyed=${activity.isDestroyed}")
                if (activity.isFinishing || activity.isDestroyed || pendingDialog != null) {
                    receiver?.send(-1, Bundle())
                    return@post
                }
                val dialog = FarmAccountDialog(activity, host) { account, service ->
                    processes.reset(target, info)
                    val extras = Bundle(args[2] as? Bundle ?: Bundle()).apply {
                        putBoolean(FarmAccountProtocol.EXTRA_CUSTOM_LOGIN, account == null)
                        if (account != null) {
                            remove(FarmAccountProtocol.EXTRA_SESSION_ID)
                            putString(FarmAccountProtocol.EXTRA_ACCOUNT, account.uin)
                            putString(FarmAccountProtocol.EXTRA_NICKNAME, account.nickname)
                        } else {
                            putString(FarmAccountProtocol.EXTRA_SESSION_ID, UUID.randomUUID().toString())
                            remove(FarmAccountProtocol.EXTRA_ACCOUNT)
                            remove(FarmAccountProtocol.EXTRA_NICKNAME)
                        }
                        putParcelable(FarmAccountProtocol.EXTRA_SERVICE, service)
                    }
                    args[2] = extras
                    pendingDialog?.launchAfterDismiss = {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            runCatching { launcher.callOriginal(target, *args) }.onFailure {
                                ActionErrorStore.report(key, "农场启动", it)
                                receiver?.send(-1, Bundle())
                            }
                        }
                    }
                }
                val lifecycle = object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityDestroyed(a: Activity) {
                        if (a === activity) { dialog.launchAfterDismiss = null; dialog.dismissImmediately() }
                    }
                    override fun onActivityCreated(a: Activity, state: Bundle?) = Unit
                    override fun onActivityStarted(a: Activity) = Unit
                    override fun onActivityResumed(a: Activity) = Unit
                    override fun onActivityPaused(a: Activity) = Unit
                    override fun onActivityStopped(a: Activity) = Unit
                    override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
                }
                dialog.setOnDismissListener {
                    hostApp.unregisterActivityLifecycleCallbacks(lifecycle)
                    pendingDialog = null
                    val launch = dialog.launchAfterDismiss
                    dialog.launchAfterDismiss = null
                    if (launch != null) launch() else receiver?.send(-1, Bundle())
                }
                pendingDialog = dialog
                hostApp.registerActivityLifecycleCallbacks(lifecycle)
                try { dialog.show() } catch (e: Exception) {
                    hostApp.unregisterActivityLifecycleCallbacks(lifecycle)
                    pendingDialog = null
                    ActionErrorStore.report(key, "农场账号选择窗口", e)
                    receiver?.send(-1, Bundle())
                }
            }
        }
    }

    private fun Activity.isTemporaryLauncher(): Boolean =
        javaClass.name == "com.tencent.mobileqq.activity.QPublicTransFragmentActivity"

    private fun Activity.canHostPicker(): Boolean =
        !isTemporaryLauncher() && !isFinishing && !isDestroyed && window?.decorView?.windowToken != null

    private fun installMiniLifecycle() {
        Instrumentation::class.java.findMethods { name = "callActivityOnCreate" }.forEach { method ->
            method.hookBefore { param ->
                (param.args.firstOrNull() as? Activity)?.intent?.let(::acceptLaunch)
            }
        }
        Instrumentation::class.java.findMethods { name = "callActivityOnNewIntent" }.forEach { method ->
            method.hookBefore { param -> param.args.filterIsInstance<Intent>().firstOrNull()?.let(::acceptLaunch) }
        }
    }

    private fun installMiniLogin() {
        loadOrThrow("com.tencent.mobileqq.mini.utils.MiniGameMMKVUtils").getMethod(
            "getString", String::class.java,
        ).hookBefore { param ->
            if (session == null) return@hookBefore
            val cacheKey = param.args[0] as? String ?: return@hookBefore
            if (!FarmAccountProtocol.isFarmPreloadKey(cacheKey)) return@hookBefore
            // QQ injects cached qqLoginData before JS starts, bypassing fastLogin entirely.
            // A marked launch must obtain its code from its own Binder capability instead.
            param.result = ""
            LogAndroid.d("[FarmAccount] Skipped foreground account's preloaded game login")
        }
        loadOrThrow("com.tencent.qqmini.sdk.cache.Storage").getMethod(
            "openContainerStorage", Context::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType,
        ).hookBefore { param ->
            val active = session?.takeIf { it.custom } ?: return@hookBefore
            if (param.args[2] != FarmAccountProtocol.APP_ID) return@hookBefore
            // This argument is only a disk-cache namespace, not a QQ/MSF account identity.
            param.args[1] = active.storageNamespace
            LogAndroid.d("[FarmAccount] Isolated custom game storage")
        }
        val proxy = loadOrThrow("com.tencent.qqmini.proxyimpl.MiniAppProxyImpl")
        // An imported code identifies the game user, not a locally authenticated QQ account.
        proxy.getMethod("getAccount").hookBefore { param -> session?.account?.let { param.result = it } }
        proxy.getMethod("getNickName").hookBefore { param -> session?.takeIf { it.account != null }?.let { param.result = it.nickname } }

        val callbackClass = loadOrThrow("com.tencent.qqmini.sdk.launcher.core.proxy.AsyncResult")
        val reply = callbackClass.getMethod("onReceiveResult", Boolean::class.javaPrimitiveType, JSONObject::class.java)
        loadOrThrow("com.tencent.qqmini.proxyimpl.ChannelProxyImpl").getMethod(
            "login", String::class.java, Long::class.javaPrimitiveType, callbackClass,
        ).hookBefore { param ->
            val active = session ?: return@hookBefore
            if (param.args[0] != FarmAccountProtocol.APP_ID) return@hookBefore
            val callback = param.args[2] ?: return@hookBefore
            param.result = null
            active.request(FarmAccountProtocol.REQUEST_LOGIN, callback, reply)
        }
        loadOrThrow("com.tencent.qqmini.proxyimpl.QQCustomizedProxyImpl").getMethod(
            "fastLogin", loadOrThrow("com.tencent.qqmini.sdk.launcher.model.MiniAppInfo"), callbackClass,
        ).hookBefore { param ->
            val active = session ?: return@hookBefore
            if (appId(param.args[0]) != FarmAccountProtocol.APP_ID) return@hookBefore
            val callback = param.args[1] ?: return@hookBefore
            param.result = null
            active.request(FarmAccountProtocol.REQUEST_FAST_LOGIN, callback, reply)
        }
    }

    private fun acceptLaunch(intent: Intent) {
        if (!intent.hasExtra("KEY_APPINFO")) return
        intent.setExtrasClassLoader(HookEnv.hostClassLoader)
        if (appId(intent.appInfo()) != FarmAccountProtocol.APP_ID) { session = null; return }
        val custom = intent.getBooleanExtra(FarmAccountProtocol.EXTRA_CUSTOM_LOGIN, false)
        val account = if (custom) null else intent.getStringExtra(FarmAccountProtocol.EXTRA_ACCOUNT)
        if (!custom && account == null) { session = null; return }
        LogAndroid.d("[FarmAccount] Accepted farm session, custom=$custom")
        // Keep a marked but broken session so login fails instead of using the foreground account.
        session = FarmSession(
            account, intent.getStringExtra(FarmAccountProtocol.EXTRA_NICKNAME).orEmpty(),
            runCatching { intent.extras?.receiver(FarmAccountProtocol.EXTRA_SERVICE) }.getOrNull(),
            custom,
            runCatching { UUID.fromString(intent.getStringExtra(FarmAccountProtocol.EXTRA_SESSION_ID)) }
                .getOrElse { UUID.randomUUID() },
        )
    }

    private class FarmSession(
        val account: String?,
        val nickname: String,
        private val service: ResultReceiver?,
        val custom: Boolean,
        sessionId: UUID,
    ) {
        val storageNamespace = "tcqt-farm-$sessionId"
        fun request(operation: Int, callback: Any, method: Method) {
            val completed = AtomicBoolean()
            lateinit var timeout: Runnable
            fun result(success: Boolean, data: Bundle?) {
                val code = data?.getString("code").orEmpty()
                LogAndroid.d("[FarmAccount] Login callback operation=$operation, success=${success && code.isNotBlank()}")
                val json = JSONObject().apply {
                    if (success && code.isNotBlank()) put(if (operation == FarmAccountProtocol.REQUEST_FAST_LOGIN) "jsCode" else "code", code)
                    else put("errMsg", data?.getString("errMsg") ?: "农场账号会话已失效，请重新打开农场")
                }
                runCatching { method.invoke(callback, success && code.isNotBlank(), json) }.onFailure {
                    ActionErrorStore.report(key, "农场登录回调", it)
                }
            }
            fun complete(success: Boolean, data: Bundle?) {
                if (completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeout)
                    result(success, data)
                }
            }
            timeout = Runnable { complete(false, null) }
            // A response and a timeout may arrive together; invoke the host callback exactly once.
            val receiver = object : ResultReceiver(mainHandler) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    complete(resultCode == FarmAccountProtocol.SUCCESS, resultData)
                }
            }
            mainHandler.postDelayed(timeout, 20_000)
            if (service == null || (!custom && !FarmAccountProtocol.isAccount(account))) receiver.send(FarmAccountProtocol.FAILURE, null)
            else service.send(operation, Bundle().apply {
                putParcelable(FarmAccountProtocol.EXTRA_REPLY, receiver.remoteReceiver())
            })
        }
    }

    private fun appId(info: Any?): String? = info?.javaClass?.getField("appId")?.get(info) as? String

    @Suppress("DEPRECATION")
    private fun Intent.appInfo(): android.os.Parcelable? = getParcelableExtra("KEY_APPINFO")
}
