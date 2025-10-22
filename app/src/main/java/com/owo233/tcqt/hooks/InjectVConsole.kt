package com.owo233.tcqt.hooks

import android.content.Context
import androidx.core.net.toUri
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient

@RegisterAction
@RegisterSetting(
    key = "inject_vconsole",
    name = "注入VConsole",
    type = SettingType.BOOLEAN,
    desc = "对宿主内置浏览器注入VConsole，方便调试。",
    uiOrder = 25
)
class InjectVConsole : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        WebViewClient::class.java.hookBeforeMethod(
            "onPageFinished",
            WebView::class.java,
            String::class.java
        ) { param ->
            val url = param.args[1] as String
            val webView = param.args[0] as WebView
            if (isBlacklisted(url)) return@hookBeforeMethod
            loadJavaScript(webView)
        }
    }

    private fun loadJavaScript(webView: WebView) {
        val jsCode = """
            (() => {
              if (window.VConsole) {
                console.log('[vConsole] 已存在，跳过加载');
                return;
              }

              const script = document.createElement('script');
              script.src = 'https://unpkg.com/vconsole@latest/dist/vconsole.min.js';
              script.async = true;

              script.onload = () => {
                try {
                  window.vConsole = new VConsole();
                } catch (error) {
                  console.error('[vConsole] 初始化失败', error);
                }
              };

              script.onerror = () => {
                console.error('[vConsole] 加载失败: 资源加载错误');
              };

              (document.head || document.body).appendChild(script);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    private fun isBlacklisted(url: String): Boolean {
        return runCatching {
            val uri = url.toUri()
            val host = uri.host?.lowercase() ?: return@runCatching false
            val port = uri.port.takeIf { it != -1 }
            val hostWithPort = if (port != null) "$host:$port" else host

            blackListUrl.any { item ->
                val normalized = item.lowercase()
                normalized == host ||
                        normalized == hostWithPort ||
                        host.endsWith(".$normalized")
            }
        }.getOrDefault(false)
    }

    companion object {
        private val blackListUrl by lazy {
            listOf(
                TCQTSetting.getSettingUrl(),
                "tcqt.dev",
                "tcqt.qq.com"
            ).map {
                it.lowercase()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .trimEnd('/')
            }.toSet()
        }
    }

    override val key: String get() = GeneratedSettingList.INJECT_VCONSOLE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
