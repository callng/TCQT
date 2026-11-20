package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.loadAs
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.getFields
import com.owo233.tcqt.core.reflect.getMethods
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qphone.base.util.BaseApplication
import com.tencent.qqnt.kernel.api.ILoginService
import com.tencent.qqnt.kernel.nativeinterface.AppInfo
import com.tencent.qqnt.kernel.nativeinterface.LoginResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import mqq.manager.MainTicketCallback
import mqq.manager.MainTicketInfo
import mqq.manager.TicketManager
import oicq.wlogin_sdk.request.WTLoginRecordSnapshot
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal object TicketManager {

    /**
     * `AddModuleEntrance` 的开关 key，必须与 `features/advanced/AddModuleEntrance.kt`
     * 中 `ActionSpec.key` 的值一致。
     */
    private const val ADD_MODULE_ENTRANCE_KEY = "add_module_entrance"

    private val ticketManagerMap = mutableMapOf<String, TicketManager>()
    private val uin: String get() = QQInterfaces.currentUin
    private var thirdSigService: Any? = null
    private var pskeyManager: Any? = null
    private val pskeyCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private var superKeyCache: String = ""
    private var superKeyCacheAt: Long = 0L

    /** pskey / superkey 有效期，避免每次请求都走一遍回调等待。 */
    private const val TICKET_CACHE_MS = 5 * 60 * 1000L

    init {
        if (TCQTSetting.getBoolean(
                ADD_MODULE_ENTRANCE_KEY
            ) && HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_70)) {
            loadOrThrow("oicq.wlogin_sdk.request.WtloginHelper")
                .getDeclaredMethod(
                    "IsNeedLoginWithPasswd",
                    String::class.java,
                    Long::class.javaPrimitiveType
                )
                .hookBefore { param -> param.result = true }
        }
    }

    private fun getTicketManager(): TicketManager {
        if (ticketManagerMap.containsKey(uin)) {
            return ticketManagerMap[uin]!!
        }
        val manager = QQInterfaces.appRuntime.getManager(2) as TicketManager
        ticketManagerMap[uin] = manager
        return manager
    }

    private fun initThirdSigService() {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_1_52) && thirdSigService == null) {
            thirdSigService = QQInterfaces.appRuntime.getRuntimeService(
                loadAs("com.tencent.mobileqq.thirdsig.api.IThirdSigService"),
                "all"
            )
        }
    }

    fun getSuperKey(): String {
        initThirdSigService()

        thirdSigService?.let { service ->
            val countDownLatch = CountDownLatch(1)
            var superKey: String? = null

            try {
                val getSuperKeyMethod = service.getMethods(false).first {
                    it.name == "getSuperKey"
                }
                val callbackClass = getSuperKeyMethod.parameterTypes.last()

                val callback = Proxy.newProxyInstance(
                    HookEnv.hostClassLoader,
                    arrayOf(callbackClass)
                ) { _, _, args ->
                    runCatching {
                        if (args.size == 2) {
                            Log.e("getSuperKey fail, code: ${args[0]}, msg: ${args[1]}")
                        } else {
                            val thirdSigInfo = args[0]
                            val fields = thirdSigInfo.getFields(false)
                                .filter { it.type == ByteArray::class.java }
                            val sigField =
                                fields.minByOrNull { it.name }!!.apply { isAccessible = true }
                            val sig = sigField.get(thirdSigInfo)
                            superKey = String(sig as ByteArray)
                        }
                        countDownLatch.countDown()
                    }.onFailure {
                        Log.e("getSuperKey fail", it)
                    }
                }
                getSuperKeyMethod.invoke(service, uin.toLong(), 16, callback)
                countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }

            return superKey ?: run {
                Log.e("getSuperKey fail, superKey is null")
                ""
            }
        }
        return getTicketManager().getSuperkey(uin)
    }

    fun getSkey(): String {
        return runCatching { getTicketManager().getRealSkey(uin) }.getOrNull().orEmpty()
    }

    /**
     * 新版 QQ 的 `TicketManager.getPskey` 只会返回本地已缓存的 pskey，
     * 未预热的域名（ti.qq.com 等）直接返回 null。XAutoDaily 3.x 的顺序是
     * 先用 `com.tencent.mobileqq.pskey.api.IPskeyManager` 主动请求，失败再退回
     * `TicketManager.getPskey`，这里保持一致。
     */
    fun getPskey(domain: String): String {
        val now = System.currentTimeMillis()
        pskeyCache[domain]?.let { (value, expireAt) ->
            if (now < expireAt) return value
        }
        val pskey = runCatching { requestPskey(domain) }.getOrNull()
            ?: runCatching { getTicketManager().getPskey(uin, domain) }.getOrNull()
        return pskey.orEmpty().also {
            if (it.isNotBlank()) pskeyCache[domain] = it to (now + TICKET_CACHE_MS)
        }
    }

    private fun initPskeyManager() {
        if (pskeyManager != null) return
        runCatching {
            pskeyManager = QQInterfaces.appRuntime.getRuntimeService(
                loadAs("com.tencent.mobileqq.pskey.api.IPskeyManager"), "all"
            )
        }.onFailure { Log.w("pskeyManager 获取失败：${it.message}") }
    }

    private fun requestPskey(domain: String): String? {
        initPskeyManager()
        val manager = pskeyManager ?: return null
        val getPskeyMethod = manager.getMethods(false).firstOrNull {
            it.name == "getPskey" && it.parameterTypes.size == 2
        } ?: return null

        val countDownLatch = CountDownLatch(1)
        var psKey: String? = null
        val callback = Proxy.newProxyInstance(
            HookEnv.hostClassLoader,
            arrayOf(getPskeyMethod.parameterTypes.last())
        ) { _, _, args ->
            runCatching {
                if (args == null || args.size == 2) {
                    Log.e("getPskey fail, code: ${args?.getOrNull(0)}, msg: ${args?.getOrNull(1)}")
                } else {
                    @Suppress("UNCHECKED_CAST")
                    psKey = (args[0] as? Map<String, String>)?.get(domain)
                }
            }
            countDownLatch.countDown()
            null
        }
        runCatching { getPskeyMethod.invoke(manager, arrayOf(domain), callback) }
            .onFailure { Log.e("getPskey 调用失败：${it.message}") }
        countDownLatch.await(15, TimeUnit.SECONDS)
        return psKey
    }

    fun getPt4Token(domain: String): String {
        return runCatching { getTicketManager().getPt4Token(uin, domain) }.getOrNull().orEmpty()
    }

    fun getStweb(): String {
        return getTicketManager().getStweb(uin)
    }

    fun getA2Sync(): String {
        return getTicketManager().getA2(uin)
    }

    fun getA2(): MainTicketInfo {
        val countDownLatch = CountDownLatch(1)
        var mainTicketInfo: MainTicketInfo? = null

        val callback = object : MainTicketCallback {
            override fun onFail(i: Int, str: String?) {
                Log.e("getA2 fail, code: $i, msg: $str")
                countDownLatch.countDown()
            }

            override fun onSuccess(mainTicketInfoResult: MainTicketInfo) {
                mainTicketInfo = mainTicketInfoResult
                countDownLatch.countDown()
            }
        }

        getTicketManager().getA2(uin.toLong(), 16, callback)
        countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
        return mainTicketInfo ?: throw Exception("获取A2失败")
    }

    fun getD2(): MainTicketInfo {
        val countDownLatch = CountDownLatch(1)
        var mainTicketInfo: MainTicketInfo? = null

        val callback = object : MainTicketCallback {
            override fun onFail(i: Int, str: String) {
                Log.e("getD2 fail, code: $i, msg: $str")
                countDownLatch.countDown()
            }

            override fun onSuccess(mainTicketInfoResult: MainTicketInfo) {
                mainTicketInfo = mainTicketInfoResult
                countDownLatch.countDown()
            }
        }

        getTicketManager().getD2(uin.toLong(), 16, callback)
        countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
        return mainTicketInfo ?: throw Exception("获取D2失败")
    }

    fun getA2AndD2(): MainTicketInfo {
        val countDownLatch = CountDownLatch(1)
        var mainTicketInfo: MainTicketInfo? = null

        val callback = object : MainTicketCallback {
            override fun onFail(i: Int, str: String) {
                Log.e("getA2AndD2 fail, code: $i, msg: $str")
                countDownLatch.countDown()
            }

            override fun onSuccess(mainTicketInfoResult: MainTicketInfo) {
                mainTicketInfo = mainTicketInfoResult
                countDownLatch.countDown()
            }
        }

        getTicketManager().getMainTicket(uin.toLong(), 16, callback)
        countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
        return mainTicketInfo ?: throw Exception("获取A2和D2失败")
    }

    suspend fun easyLogin(): LoginResult {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_70)) {
            val appInfo = AppInfo().apply {
                appId = 16L
                appName = "com.tencent.mobileqq"
                qua = BaseApplication.getContext().qua
            }

            return withTimeout(10000L.milliseconds) {
                suspendCancellableCoroutine { cont ->
                    val api = QRoute.api(ILoginService::class.java)
                    api.easyLogin(QQInterfaces.currentUin.toLong(), appInfo) { code, msg, result ->
                        if (code == 0 && result != null) {
                            cont.resumeWith(Result.success(result))
                        } else {
                            cont.resumeWith(Result.failure(
                                EasyLoginException(code, "easyLogin fail, code: $code, msg: $msg")
                            ))
                        }
                    }

                    cont.invokeOnCancellation {
                        Log.w("easyLogin cancelled")
                    }
                }
            }
        } else throw UnsupportedOperationException("easyLogin not supported")
    }

    fun getWTLoginRecordSnapshot(): WTLoginRecordSnapshot {
        return getTicketManager().getWTLoginRecordSnapshot(uin.toLong(), 16)
    }

    fun getCookie(domain: String): String {
        val pUin = StringBuilder().append('o')
        for (i in 0 until 10 - uin.length) {
            pUin.append('0')
        }
        pUin.append(uin)
        val cookies = linkedMapOf<String, String>()
        cookies["uin"] = pUin.toString()
        cookies["p_uin"] = pUin.toString()
        fun put(name: String, value: String) {
            if (value.isNotBlank() && value != "null") cookies[name] = value
        }
        put("skey", getSkey())
        put("p_skey", getPskey(domain))
        put("pt4Token", getPt4Token(domain))
        put("superkey", superKeyForCookie())
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** superkey 用于 Web 侧鉴权，XAutoDaily 的 cookie 里同样会带上它。 */
    private fun superKeyForCookie(): String {
        val now = System.currentTimeMillis()
        if (now - superKeyCacheAt < TICKET_CACHE_MS) return superKeyCache
        superKeyCache = runCatching { getSuperKey() }.getOrDefault("")
        superKeyCacheAt = now
        return superKeyCache
    }
}

class EasyLoginException(
    val code: Int,
    override val message: String
) : Exception(message)
