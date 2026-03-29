package com.owo233.tcqt.hooks.func.misc

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.servlet.MiniSvc
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import java.util.WeakHashMap

@RegisterAction
@RegisterSetting(
    key = "card_fetcher",
    name = "自动领取达人补登卡",
    type = SettingType.BOOLEAN,
    desc = "在进入QQ达人页时自动领取当天的补登卡，不用再玩**的**小程序游戏！",
    uiTab = "杂项"
)
class CardFetcher : IAction {

    override val key: String
        get() = GeneratedSettingList.CARD_FETCHER

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.TOOL)

    override fun onRun(ctx: Context, process: ActionProcess) {
        WebViewClient::class.java.hookMethodAfter(
            "onPageFinished",
            WebView::class.java,
            String::class.java
        ) { param ->
            val webView = param.args[0] as WebView
            val url = param.args[1] as String

            if (!isTargetUrl(url)) return@hookMethodAfter
            if (!markTriggered(webView, url)) return@hookMethodAfter

            SyncUtils.postDelayed(1500L) {
                runCatching {
                    MiniSvc.judgeTiming()
                    Toasts.success("已领取补登卡")
                }
            }
        }
    }

    private fun isTargetUrl(url: String): Boolean {
        return url != "about:blank" && url.startsWith(QQ_DAREN_URL_PREFIX)
    }

    private fun markTriggered(webView: WebView, url: String): Boolean {
        synchronized(triggerHistory) {
            val lastUrl = triggerHistory[webView]
            if (lastUrl == url) return false
            triggerHistory[webView] = url
            return true
        }
    }

    private companion object {
        private const val QQ_DAREN_URL_PREFIX = "https://ti.qq.com/qqdaren/index"
        private val triggerHistory = WeakHashMap<WebView, String>()
    }
}
