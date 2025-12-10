package com.owo233.tcqt.hooks

import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.FuzzyClassKit
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "browser_restrict_mitigation",
    name = "禁用内置浏览器网页拦截",
    type = SettingType.BOOLEAN,
    desc = "允许在内置浏览器中访问非官方认可的网页。",
    uiTab = "杂项"
)
class BrowserRestrictMitigation : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        (FuzzyClassKit.findMethodByClassPrefix(
            prefix = "com.tencent.mobileqq.webview.WebSecurityPluginV2",
            isSubClass = true
        ) { _, method ->
            method.paramCount == 1 && method.parameterTypes[0] == Bundle::class.java
        } ?: error("BrowserRestrictMitigation: 找不到目标方法..")).hookBeforeMethod {
            val bundle = it.args[0] as Bundle
            if (bundle.getInt("jumpResult", 0) != 0) {
                bundle.putInt("jumpResult", 0)
                bundle.putString("jumpUrl", "")
            }
        }
    }

    override val key: String get() = GeneratedSettingList.BROWSER_RESTRICT_MITIGATION

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
