package com.owo233.tcqt.features.misc

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findField

@RegisterAction
object MiniAppShare : Feature(
    key = "mini_app_share",
    name = "修改小程序分享行为",
    desc = "取消小程序分享时仍返回成功。可选择跳过好友、群分享页面；无法区分奖励分享与普通分享，跳过会同时影响两者。",
    processes = setOf(ActionProcess.ALL),
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
) {

    private val sharePage by intOption(
        "share_page", "分享页面",
        defaultValue = 1,
        options = listOf("保留分享页面", "跳过分享页面"),
    )
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun install() {
        loadOrThrow("eipc.EIPCClient").hookMethodBefore(
            "callServer",
            String::class.java, String::class.java,
            Bundle::class.java, loadOrThrow("eipc.EIPCResultCallback")
        ) { param ->
            val module = param.args[0] as String
            if (module != "MiniMsgIPCServer") return@hookMethodBefore

            when (param.args[1] as String) {
                "cmd_mini_share_fail" -> {
                    param.args[1] = "cmd_mini_share_suc"
                }

                "cmd_mini_report_event" -> {
                    (param.args[2] as? Bundle)
                        ?.takeIf { it.getString("key_mini_report_event_reserves2") == "fail" }
                        ?.putString("key_mini_report_event_reserves2", "success")
                }
            }
        }

        loadOrThrow("com.tencent.mobileqq.forward.ForwardBaseOption").hookMethodBefore(
            "endForwardCallback",
            Boolean::class.javaPrimitiveType,
        ) { param ->
            param.args[0] = true
        }

        if (sharePage == MiniAppShareProtocol.SKIP_PAGE) installSkipSharePage()
    }

    private fun installSkipSharePage() {
        val innerShareData = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.InnerShareData")
        val shareData = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.ShareData")
        val notifyResult = shareData.getMethod(
            "notifyShareResult", Context::class.java,
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        )
        val fields = MiniAppShareDataFields(shareData, innerShareData)

        fun skip(data: Any, activity: Activity? = null, completion: (() -> Unit)? = null): Boolean {
            if (!innerShareData.isInstance(data)) return false
            // 好友代付也复用 InnerShareData（source=13、scene=4），仅处理已确认的分享来源。
            // 游戏图片的专用入口没有设置 shareSource，使用独立的 Activity 参数识别。
            if (activity == null && !MiniAppShareProtocol.isStandardShare(
                    fields.source.getInt(data), fields.scene.getInt(data),
                )) return false
            val appId = fields.appId(data)
            if (!MiniAppShareProtocol.shouldSkip(sharePage, fields.target.getInt(data), appId)) {
                return false
            }
            val context = activity ?: fields.activity.get(data) as? Activity ?: return false
            // ShareJsPlugin 已保存 ShareState 的事件和 callbackId；下一次主线程调度再完成。
            // 强制通知绕过尚未请求到的 needShareCallback，使用宿主按 appId 路由的回调，
            // 不再额外发送全局 cmd_mini_share_suc，避免一次分享触发两次成功回调。
            return mainHandler.post {
                ActionErrorStore.withAction(key) {
                    runCatching {
                        if (completion != null) completion()
                        else notifyResult.invoke(data, context, 0, true)
                    }.onFailure {
                        ActionErrorStore.report(key, "小程序分享完成回调", it)
                        Log.e("小程序分享完成回调失败", it)
                    }
                }
            }
        }

        // 在 SDK 数据入口拦截，覆盖 ForwardRecentActivity、新版好友选择器和快捷面板。
        // 不依赖通用 Activity 的名字，聊天里的普通转发不会经过这两个入口。
        listOf(
            innerShareData.getMethod("shareAppMessage"),
            innerShareData.getMethod(
                "shareAppPictureMessage",
                loadOrThrow("com.tencent.qqmini.sdk.launcher.core.IMiniAppContext"),
            ),
        ).forEach { method ->
            method.hookBefore { param ->
                if (skip(param.thisObject)) param.result = null
            }
        }

        // shareGamePicture 直接调用代理，Activity 在参数中而不在 fromActivity 中。
        loadOrThrow("com.tencent.qqmini.proxyimpl.ShareProxyImpl").getMethod(
            "shareGamePicture", Activity::class.java, shareData,
        ).hookBefore { param ->
            val data = param.args[1] ?: return@hookBefore
            val activity = param.args[0] as? Activity ?: return@hookBefore
            if (skip(data, activity)) param.result = true
        }

        // 结构化游戏分享使用独立 AsyncResult，不走 ShareState；只完成传入的回调。
        val asyncResult = loadOrThrow("com.tencent.qqmini.sdk.launcher.core.proxy.AsyncResult")
        val onReceiveResult = asyncResult.getMethod(
            "onReceiveResult", Boolean::class.javaPrimitiveType, org.json.JSONObject::class.java,
        )
        loadOrThrow("com.tencent.qqmini.proxyimpl.ShareProxyImpl").getMethod(
            "shareGameStructMessage", shareData, asyncResult,
        ).hookBefore { param ->
            val data = param.args[0] ?: return@hookBefore
            if (!innerShareData.isInstance(data)) return@hookBefore
            val activity = fields.activity.get(data) as? Activity ?: return@hookBefore
            val callback = param.args[1] ?: return@hookBefore
            if (skip(data, activity) { onReceiveResult.invoke(callback, true, null) }) {
                param.result = null
            }
        }
    }
}

internal class MiniAppShareDataFields(shareData: Class<*>, innerShareData: Class<*>) {
    val target = shareData.findField { name = "shareTarget"; isStatic = false }
    val source = shareData.findField { name = "shareSource"; isStatic = false }
    val scene = innerShareData.findField { name = "shareScene"; isStatic = false }
    val activity = innerShareData.findField { name = "fromActivity"; isStatic = false }
    // miniAppInfo 是 protected，appId 则由 MiniAppBaseInfo 继承。
    private val info = shareData.findField { name = "miniAppInfo"; isStatic = false }

    fun appId(data: Any): String? {
        val miniAppInfo = info.get(data) ?: return null
        return miniAppInfo.javaClass.findField { name = "appId"; isStatic = false }
            .get(miniAppInfo) as? String
    }
}

internal object MiniAppShareProtocol {
    const val SKIP_PAGE = 2
    private val qqTargets = setOf(0, 2, 5, 6)

    fun isStandardShare(source: Int, scene: Int): Boolean =
        source in 11..12 && scene == 1

    // ShareProxyImpl / InnerShareData.getShareType：
    // 0=QQ，2=QQ 直接分享，5=指定聊天，6=扩展好友入口；非奖励类型标记。
    fun shouldSkip(pageMode: Int, shareTarget: Int, appId: String?): Boolean =
        pageMode == SKIP_PAGE && !appId.isNullOrBlank() &&
            shareTarget in qqTargets
}
