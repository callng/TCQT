package com.owo233.tcqt.features.cleanup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
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
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.findMethods
import java.util.WeakHashMap

@RegisterAction
object SkipMiniAppRewardAd : Feature(
    key = "skip_mini_app_reward_ad",
    name = "跳过小程序激励广告",
    desc = "点击领取奖励时跳过小程序、小游戏的 GDT 激励广告，并返回观看完成结果。",
    processes = setOf(ActionProcess.ALL),
    priority = ActionPriority.EARLY,
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
) {

    private const val START_PARAMS = "com.tencent.gdtad.basics.motivevideo.data.StartGdtMotiveVideoParams"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val openingActivities = WeakHashMap<Activity, ResultReceiver>()

    override fun install() {
        hookAdActivityLaunch()
        hookAdActivityCreated()
        hookGdtEntry()
        hookAmsFallback()
    }

    private fun hookGdtEntry() {
        "com.tencent.gdtad.impl.GdtAPIImpl".toClass.findMethod {
            name = "startGdtMotiveVideo"
            paramCount = 1
            paramTypes(START_PARAMS.toClass)
        }.hookBefore { param ->
            val params = param.args.firstOrNull() ?: return@hookBefore
            val data = params.javaClass.getField("data").get(params)
            val receiver = params.javaClass.getField("resultReceiver").get(params)
                    as? ResultReceiver ?: return@hookBefore
            if (isMiniReward(data, receiver) && postReward(receiver)) {
                param.result = null
            }
        }
    }

    private fun hookAdActivityLaunch() {
        Instrumentation::class.java.findMethods {
            name = "execStartActivity"
        }.forEach { method ->
            val intentIndex = method.parameterTypes.indexOf(Intent::class.java)
            method.hookBefore { param ->
                val intent = param.args[intentIndex] as? Intent ?: return@hookBefore
                val receiver = rewardReceiver(intent) ?: return@hookBefore
                if (postReward(receiver)) {
                    param.result = null
                }
            }
        }
    }

    private fun hookAdActivityCreated() {
        Instrumentation::class.java.findMethods {
            name = "callActivityOnCreate"
        }.forEach { method ->
            method.hookBefore { param ->
                val activity = param.args.firstOrNull() as? Activity ?: return@hookBefore
                if (activity in openingActivities) return@hookBefore
                val intent = activity.intent ?: return@hookBefore
                val receiver = rewardReceiver(intent) ?: return@hookBefore
                // 跨进程送达的页面兜底 让 onCreate 正常完成 避免 SuperNotCalledException
                // 页面只拿到空接收器 关闭/销毁时不会再向小游戏发送一次取消结果
                activity.intent = Intent(intent).putExtra("result_receiver", ResultReceiver(null))
                openingActivities[activity] = receiver
            }
            method.hookAfter { param ->
                val activity = param.args.firstOrNull() as? Activity ?: return@hookAfter
                val receiver = openingActivities.remove(activity) ?: return@hookAfter
                if (param.throwable != null) {
                    activity.intent.putExtra("result_receiver", receiver)
                    return@hookAfter
                }
                activity.finish()
                postReward(receiver)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun rewardReceiver(intent: Intent): ResultReceiver? {
        if (!intent.hasExtra("result_receiver") || !intent.hasExtra("data")) return null
        val extras = intent.extras ?: return null
        extras.classLoader = HookEnv.hostClassLoader
        val receiver = extras.getParcelable("result_receiver") as? ResultReceiver ?: return null
        return receiver.takeIf { isMiniReward(extras.getSerializable("data"), it) }
    }

    private fun hookAmsFallback() {
        val method = "com.tencent.mobileqq.minigame.ad.ams.FallbackKuiklyLauncher".toClass.findMethod {
            name = "open"
            paramCount = 6
            paramTypes(null, null, null, null, null,
                $$"com.tencent.mobileqq.minigame.ad.ams.FallbackKuiklyLauncher$OnCloseCallback".toClass)
        }
        val onClose = method.parameterTypes.last().getMethod("onClose", Boolean::class.javaPrimitiveType)
        method.hookBefore { param ->
            val callback = param.args.lastOrNull() ?: return@hookBefore
            if (!MiniRewardAdProtocol.isAmsFallback(callback.javaClass.name)) return@hookBefore
            // showAD 已经保存 mShowing 异步关闭 让原调用先完成 onADShow 和展示状态更新
            if (postCompletion { onClose.invoke(callback, true) }) param.result = null
        }
    }

    private fun isMiniReward(data: Any?, receiver: ResultReceiver): Boolean {
        if (data?.javaClass?.name != MiniRewardAdProtocol.PAGE_DATA) return false
        val refId = data.javaClass.getField("refId").get(data) as? String
        return MiniRewardAdProtocol.isMiniReward(data.javaClass.name, receiver.javaClass.name, refId)
    }

    private fun postReward(receiver: ResultReceiver): Boolean = postCompletion {
        // 不伪造曝光、点击或观看时长，保留 QQ 自身后续的奖励处理和服务端校验
        receiver.send(Activity.RESULT_OK, Bundle().apply {
            putBoolean(MiniRewardAdProtocol.PROFITABLE_FLAG, true)
        })
    }

    private fun postCompletion(block: () -> Unit): Boolean = mainHandler.post {
        ActionErrorStore.withAction(key) {
            runCatching(block).onFailure {
                ActionErrorStore.report(key, "激励广告完成回调", it)
                Log.e("小程序激励广告完成回调失败", it)
            }
        }
    }
}

internal object MiniRewardAdProtocol {
    const val PAGE_DATA = "com.tencent.gdtad.basics.motivevideo.data.GdtMotiveVideoPageData"
    const val PROFITABLE_FLAG = "profitable_flag"

    private val rewardReceivers = setOf(
        $$"com.tencent.qqmini.proxyimpl.AdProxyImpl$AdResultReceiver",
        $$"com.tencent.mobileqq.minigame.ad.ams.AmsRewardedVideoAdView$AmsMotiveVideoResultReceiver",
    )

    fun isMiniReward(dataClass: String?, receiverClass: String, refId: String?): Boolean =
        dataClass == PAGE_DATA &&
            (receiverClass in rewardReceivers ||
                (receiverClass == "android.os.ResultReceiver" && refId == "biz_src_miniapp"))

    fun isAmsFallback(className: String): Boolean =
        className == $$"com.tencent.mobileqq.minigame.ad.ams.AmsRewardedVideoAdView$2"
}
