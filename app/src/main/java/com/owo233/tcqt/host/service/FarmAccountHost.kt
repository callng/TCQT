package com.owo233.tcqt.host.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import android.os.SystemClock
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.LogAndroid
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qphone.base.remote.FromServiceMsg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mqq.app.MobileQQ
import mqq.app.NewIntent
import org.json.JSONObject
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

/** QQ adapters; credentials stay in MSF and each request carries its own account. */
@Suppress("DEPRECATION") // QQ's verified send path exposes ToServiceMsg.extraData.
internal class FarmAccountHost(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private class CodeRequest(val uin: String, val id: Long) {
        val routed = AtomicBoolean()
    }
    private val requestSequence = AtomicLong()
    private val requests = ConcurrentHashMap<Long, CodeRequest>()
    private val fastRequestAccount = ThreadLocal<CodeRequest>()
    private val cmdUtilClass = loadOrThrow("com.tencent.mobileqq.mini.reuse.MiniAppCmdUtil")
    private val cmdCallbackClass = loadOrThrow("com.tencent.mobileqq.mini.reuse.MiniAppCmdInterface")
    private val loginServlet = loadOrThrow("com.tencent.mobileqq.mini.servlet.MiniAppGetLoginCodeServlet")
    private val newIntentConstructor = loadOrThrow(
        $$"com.tencent.mobileqq.mini.reuse.MiniAppCmdUtil$NewIntent",
    ).getDeclaredConstructor(
        cmdUtilClass, Context::class.java, Class::class.java, cmdCallbackClass, String::class.java,
    ).apply { isAccessible = true }
    private val farmHandlerClass = loadOrThrow("com.tencent.mobileqq.minigame.nqf.data.NQFFarmHandler")
    private val fastCallbackClass = loadOrThrow("com.tencent.mobileqq.minigame.nqf.data.INQFRequestCallback")
    private val fastRequest = farmHandlerClass.getMethod(
        "requestFastLoginInfo", String::class.java, fastCallbackClass,
    )
    private val canAutoLogin = loadOrThrow("mqq.app.AutoLoginUtil")
        .getMethod("canAutoLogin", String::class.java)
    private val nicknameApi by lazy {
        runCatching {
            val type = loadOrThrow("com.tencent.mobileqq.login.account.ILoginAccountInfoApi")
            val api = loadOrThrow("com.tencent.mobileqq.qroute.QRoute")
                .getMethod("api", Class::class.java).invoke(null, type)
            api to type.getMethod("getNick", String::class.java)
        }.getOrNull()
    }

    fun installRoutingHooks() {
        loadOrThrow("com.tencent.mobileqq.msf.sdk.MsfServiceSdk").getMethod(
            "sendMsg", ToServiceMsg::class.java,
        ).hookBefore { param ->
            val msg = param.args[0] as? ToServiceMsg ?: return@hookBefore
            if (!FarmAccountProtocol.mayRoute(msg.serviceCmd)) return@hookBefore
            val account = msg.getAttribute(FarmAccountProtocol.EXTRA_ACCOUNT) as? String
            if (account != null) {
                val id = msg.getAttribute(FarmAccountProtocol.EXTRA_REQUEST) as? Long
                val request = id?.let(requests::get)
                if (!FarmAccountProtocol.isAccount(account) || request == null || request.uin != account) {
                    // Frameworks catch exceptions thrown by before callbacks and still call
                    // the original method. Set its throwable explicitly to stop this send.
                    param.throwable = IllegalStateException("农场登录请求已取消或失效")
                    return@hookBefore
                }
                msg.uin = account
                msg.addAttribute("uinNotMatch", true)
                request.routed.set(true)
                LogAndroid.d("[FarmAccount] Sending ${msg.serviceCmd}, current=${account == QQInterfaces.appRuntime.account}")
            }
        }
        // NewIntent normally uses the current runtime's Packet. Route at the last send point.
        loadOrThrow("mqq.app.MSFServlet").findMethod {
            name = "sendToMSF"
            paramTypes(Intent::class.java, ToServiceMsg::class.java)
        }.hookBefore { param ->
            val intent = param.args[0] as? Intent
            val msg = param.args[1] as? ToServiceMsg ?: return@hookBefore
            val account = intent?.getStringExtra(FarmAccountProtocol.EXTRA_ACCOUNT)
                ?: (msg.getAttribute(FarmAccountProtocol.EXTRA_ACCOUNT) as? String)
                ?: msg.extraData?.getString(FarmAccountProtocol.EXTRA_ACCOUNT)
                ?: return@hookBefore
            // A marked request must never silently fall back to the foreground QQ account.
            if (!FarmAccountProtocol.isAccount(account) || !FarmAccountProtocol.mayRoute(msg.serviceCmd)) {
                param.throwable = IllegalStateException("农场登录请求的账号或命令无效")
            } else {
                msg.uin = account
                msg.addAttribute(FarmAccountProtocol.EXTRA_ACCOUNT, account)
                if (intent?.hasExtra(FarmAccountProtocol.EXTRA_REQUEST) == true) {
                    msg.addAttribute(FarmAccountProtocol.EXTRA_REQUEST, intent.getLongExtra(FarmAccountProtocol.EXTRA_REQUEST, -1))
                }
                // MainService intentionally supports request-local cross-account replies via
                // this attribute; otherwise isValidAccount drops the selected account's reply.
                msg.addAttribute("uinNotMatch", true)
            }
        }
        loadOrThrow("mqq.app.MainService").findMethod {
            name = "receiveMessageFromMSF"
            paramTypes(ToServiceMsg::class.java, FromServiceMsg::class.java)
        }.hookBefore { param ->
            val request = param.args[0] as? ToServiceMsg ?: return@hookBefore
            if (request.getAttribute(FarmAccountProtocol.EXTRA_ACCOUNT) == null ||
                !FarmAccountProtocol.mayRoute(request.serviceCmd)) return@hookBefore
            val response = param.args[1] as? FromServiceMsg ?: return@hookBefore
            LogAndroid.d("[FarmAccount] Reply ${request.serviceCmd}, result=${response.resultCode}")
        }
        loadOrThrow("com.tencent.mobileqq.app.BaseBusinessHandler").getMethod(
            "sendPbReq", ToServiceMsg::class.java,
        ).hookBefore { param ->
            if (!farmHandlerClass.isInstance(param.thisObject)) return@hookBefore
            val request = fastRequestAccount.get() ?: return@hookBefore
            val msg = param.args[0] as? ToServiceMsg ?: return@hookBefore
            if (msg.serviceCmd != FarmAccountProtocol.FAST_LOGIN_CMD) return@hookBefore
            // TempServlet rebuilds Packet, copying attributes but dropping extraData.
            msg.addAttribute(FarmAccountProtocol.EXTRA_ACCOUNT, request.uin)
            msg.addAttribute(FarmAccountProtocol.EXTRA_REQUEST, request.id)
            msg.setIsSupportRetry(false)
        }
    }

    suspend fun accounts(): List<FarmAccount> = withContext(Dispatchers.IO) {
        val current = MobileQQ.getMobileQQ().peekAppRuntime()?.account.orEmpty()
        MobileQQ.getMobileQQ().allAccounts.orEmpty().toList().mapNotNull { account ->
            val uin = account.uin
            if (!FarmAccountProtocol.isAccount(uin)) return@mapNotNull null
            // Saved secondary accounts need not be marked online; QQ's auto-login state includes them.
            if (uin != current && !account.isLogined && canAutoLogin.invoke(null, uin) != true) {
                return@mapNotNull null
            }
            val nickname = account.getAttribute("_nick", "").ifBlank {
                runCatching { nicknameApi?.let { (api, method) -> method.invoke(api, uin) as? String } }
                    .getOrNull().orEmpty()
            }
            FarmAccount(uin, nickname, uin == current)
        }.distinctBy { it.uin }.sortedByDescending { it.isCurrent }
    }

    private suspend fun requireAccount(account: FarmAccount) {
        check(accounts().any { it.uin == account.uin }) { "该账号已退出登录，请先在 QQ 中重新登录" }
    }

    suspend fun prepare(account: FarmAccount): ResultReceiver {
        requireAccount(account)
        val code = requestCode(account.uin, FarmAccountProtocol.REQUEST_FAST_LOGIN)
        val preflight = FarmPreflightCode(account.uin, code, SystemClock.elapsedRealtime())
        // This unexported Binder capability is handed only to this farm launch. No tickets or code on disk.
        return object : ResultReceiver(mainHandler) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val reply = resultData?.receiver(FarmAccountProtocol.EXTRA_REPLY) ?: return
                if (resultCode !in FarmAccountProtocol.REQUEST_LOGIN..FarmAccountProtocol.REQUEST_FAST_LOGIN) {
                    reply.send(FarmAccountProtocol.FAILURE, Bundle().apply { putString("errMsg", "登录请求无效") })
                    return
                }
                scope.launch {
                    try {
                        requireAccount(account)
                        val fresh = preflight.take(account.uin, resultCode, SystemClock.elapsedRealtime())
                            ?: requestCode(account.uin, resultCode)
                        reply.send(FarmAccountProtocol.SUCCESS, Bundle().apply { putString("code", fresh) })
                    } catch (_: TimeoutCancellationException) {
                        reply.send(FarmAccountProtocol.FAILURE, Bundle().apply {
                            putString("errMsg", "农场登录超时，请检查网络后重新打开农场")
                        })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        reply.send(FarmAccountProtocol.FAILURE, Bundle().apply {
                            putString("errMsg", "该账号的农场登录失败，请重新打开农场或在 QQ 中重新登录该账号")
                        })
                    }
                }
            }
        }.remoteReceiver()
    }

    private suspend fun requestCode(uin: String, operation: Int): String = withTimeout(15_000.milliseconds) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val request = CodeRequest(uin, requestSequence.incrementAndGet())
                requests[request.id] = request
                continuation.invokeOnCancellation { requests.remove(request.id) }
                val completed = AtomicBoolean()
                fun complete(code: String?) {
                    if (!completed.compareAndSet(false, true) || !continuation.isActive) return
                    requests.remove(request.id)
                    if (!request.routed.get()) continuation.resumeWithException(
                        IllegalStateException("QQ 未按所选账号发送农场登录请求，请更新模块后重试"),
                    ) else if (code.isNullOrBlank()) continuation.resumeWithException(
                        IllegalStateException("未能获取该账号的农场登录信息，请重试或在 QQ 中重新登录该账号"),
                    ) else continuation.resume(code)
                }
                try {
                    if (operation == FarmAccountProtocol.REQUEST_FAST_LOGIN) {
                        val callback = callback(fastCallbackClass) { name, args ->
                            when (name) {
                                "onSuccess" -> {
                                    complete(runCatching {
                                        val response = args?.firstOrNull()
                                        val field = response?.javaClass?.getField("js_code")?.get(response)
                                        field?.javaClass?.getMethod("get")?.invoke(field) as? String
                                    }.getOrNull())
                                }
                                "onFail" -> complete(null)
                            }
                        }
                        val handler = farmHandlerClass.getMethod("get").invoke(null)
                            ?: error("QQ 农场服务尚未就绪，请稍后重试")
                        fastRequestAccount.set(request)
                        try {
                            fastRequest.invoke(handler, FarmAccountProtocol.APP_ID, callback)
                        } finally {
                            fastRequestAccount.remove()
                        }
                    } else {
                        val callback = callback(cmdCallbackClass) { name, args ->
                            if (name == "onCmdListener") {
                                complete(if (args?.get(0) == true) (args[1] as? JSONObject)?.optString("code") else null)
                            }
                        }
                        val util = cmdUtilClass.getMethod("getInstance").invoke(null)
                        val intent = newIntentConstructor.newInstance(
                            util, context, loginServlet, callback, "getLoginCode",
                        ) as NewIntent
                        intent.putExtra("key_appid", FarmAccountProtocol.APP_ID)
                        intent.putExtra("key_uin", uin.toLong())
                        intent.putExtra("timeout", 12_000L)
                        intent.putExtra(FarmAccountProtocol.EXTRA_ACCOUNT, uin)
                        intent.putExtra(FarmAccountProtocol.EXTRA_REQUEST, request.id)
                        QQInterfaces.appRuntime.startServlet(intent)
                    }
                } catch (e: Exception) {
                    requests.remove(request.id)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        }
    }

    private fun callback(type: Class<*>, handle: (String, Array<out Any?>?) -> Unit): Any =
        Proxy.newProxyInstance(HookEnv.hostClassLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "TCQT farm login callback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> { handle(method.name, args); null }
            }
        }
}

@Suppress("DEPRECATION")
internal fun Bundle.receiver(key: String): ResultReceiver? = getParcelable(key) as? ResultReceiver

/** Bundle must write the framework class name, not an anonymous module-only subclass. */
internal fun ResultReceiver.remoteReceiver(): ResultReceiver {
    val parcel = Parcel.obtain()
    return try {
        writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        ResultReceiver.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}
