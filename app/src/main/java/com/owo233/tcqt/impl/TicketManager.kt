package com.owo233.tcqt.impl

import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.XpClassLoader.hostClassLoader
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.getFields
import com.owo233.tcqt.utils.getMethods
import mqq.manager.MainTicketCallback
import mqq.manager.MainTicketInfo
import mqq.manager.TicketManager
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object TicketManager {

    private val ticketManagerMap = mutableMapOf<String, TicketManager>()
    val currentUin: String get() = "${QQInterfaces.appRuntime.longAccountUin}"
    private var thirdSigService: Any? = null

    private fun getTicketManager(): TicketManager {
        if (ticketManagerMap.containsKey(currentUin)) {
            return ticketManagerMap[currentUin]!!
        }
        val manager = QQInterfaces.appRuntime.getManager(2) as TicketManager
        ticketManagerMap[currentUin] = manager
        return manager
    }

    private fun initThirdSigService() {
        if (PlatformTools.getHostVersionCode() > PlatformTools.QQ_9_1_52_VER && thirdSigService == null) {
            thirdSigService = QQInterfaces.appRuntime.getRuntimeService(
                XpClassLoader.loadAs("com.tencent.mobileqq.thirdsig.api.IThirdSigService"),
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

                val callback = Proxy.newProxyInstance(hostClassLoader, arrayOf(callbackClass)) { _, method, args ->
                    runCatching {
                        if (args.size == 2) {
                            Log.e("getSuperKey fail, code: ${args[0]}, msg: ${args[1]}")
                        } else {
                            val thirdSigInfo = args[0]
                            val fields = thirdSigInfo.getFields(false)
                                .filter { it.type == ByteArray::class.java }
                            val sigField = fields.minByOrNull { it.name }!!.apply { isAccessible = true}
                            val sig = sigField.get(thirdSigInfo)
                            superKey = String(sig as ByteArray)
                        }
                        countDownLatch.countDown()
                    }.onFailure {
                        Log.e("getSuperKey fail", it)
                    }
                }
                getSuperKeyMethod.invoke(service, currentUin, 16, callback)
                countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
        }
        return getTicketManager().getSuperkey(currentUin)
    }

    fun getSkey(): String {
        return getTicketManager().getRealSkey(currentUin)
    }

    fun getPskey(domain: String): String {
        return getTicketManager().getPskey(currentUin, domain)
    }

    fun getPt4Token(domain: String): String {
        return getTicketManager().getPt4Token(currentUin, domain)
    }

    fun getStweb(): String {
        return getTicketManager().getStweb(currentUin)
    }

    fun getA2Sync(): String {
        return getTicketManager().getA2(currentUin)
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

        getTicketManager().getA2(currentUin.toLong(), 16, callback)
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

        getTicketManager().getD2(currentUin.toLong(), 16, callback)
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

        getTicketManager().getMainTicket(currentUin.toLong(), 16, callback)
        countDownLatch.await(15000L, TimeUnit.MILLISECONDS)
        return mainTicketInfo ?: throw Exception("获取A2和D2失败")
    }

    fun getCookie(domain: String): String {
        var uin = currentUin
        val skey = getSkey()
        val pksey = getPskey(domain)
        val pt4Token = getPt4Token(domain)
        val puin = StringBuilder().append('o')
        for (i in 0 until 10 - uin.length) {
            puin.append('0')
        }
        puin.append(uin)
        uin = puin.toString()
        val cookiesMap: MutableMap<String, String> = HashMap()
        cookiesMap["uin"] = uin
        cookiesMap["p_uin"] = uin
        cookiesMap["skey"] = skey
        cookiesMap["p_skey"] = pksey
        cookiesMap["pt4Token"] = pt4Token
        return buildString {
            cookiesMap.forEach { (key, value) ->
                append("$key=$value; ")
            }
        }.removeSuffix("; ")
    }
}
