package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient

@RegisterAction
@RegisterSetting(
    key = "inject_console",
    name = "注入Console",
    type = SettingType.BOOLEAN,
    desc = "对宿主内置浏览器注入Console，方便调试。",
    uiOrder = 25
)
class InjectConsole : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        WebViewClient::class.java.hookBeforeMethod(
            "onPageFinished",
            WebView::class.java,
            String::class.java
        ) { param ->
            val webView = param.args[0] as WebView
            loadJavaScriptByEruda(webView)
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

    private fun loadJavaScriptByEruda(webView: WebView) {
        val jsCode = """
            (() => {
                if (window.eruda) {
                    console.log('[Eruda] 已存在，跳过加载');
                    return;
                }

                const script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                script.async = true;

                script.onload = () => {
                    try {
                        eruda.init();
                        console.log('[Eruda] 初始化成功');
                    } catch (error) {
                        console.error('[Eruda] 初始化失败', error);
                    }
                };

                script.onerror = () => {
                    console.error('[Eruda] 加载失败: 资源加载错误');
                };

                (document.head || document.body).appendChild(script);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override val key: String get() = GeneratedSettingList.INJECT_CONSOLE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
