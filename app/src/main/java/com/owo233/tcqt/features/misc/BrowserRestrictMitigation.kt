package com.owo233.tcqt.features.misc

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction(processes = [ActionProcess.TOOL])
object BrowserRestrictMitigation : Feature(
    key = "browser_restrict_mitigation",
    name = "禁用内置浏览器网页拦截",
    desc = "允许在内置浏览器中访问非官方认可的网页。",
) {

    private var targetUrl: String? = null

    override fun install() {
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


    const val BLOCK_URL = "c.pc.qq.com"
}
