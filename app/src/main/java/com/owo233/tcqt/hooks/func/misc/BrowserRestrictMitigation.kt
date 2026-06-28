package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class BrowserRestrictMitigation : IAction {

    private var targetUrl: String? = null

    override val key: String get() = "browser_restrict_mitigation"
    override val name: String get() = "禁用内置浏览器网页拦截"
    override val desc: String get() = "允许在内置浏览器中访问非官方认可的网页。"
    override val uiTab: String get() = "杂项"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.biz.pubaccount.CustomWebView".toClass.findMethod {
            name = "loadUrl"
            paramTypes = arrayOf(string)
        }.hookReplace { param ->
            val url = param.args[0] as String

            if (url.contains(BLOCK_URL)) {
                return@hookReplace param.invokeOriginal(arrayOf(targetUrl))
            } else {
                targetUrl = url
                return@hookReplace param.invokeOriginal()
            }
        }
    }

    private companion object {
        const val BLOCK_URL = "c.pc.qq.com"
    }
}
