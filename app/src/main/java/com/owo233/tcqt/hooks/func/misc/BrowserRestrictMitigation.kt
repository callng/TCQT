package com.owo233.tcqt.hooks.func.misc

import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

@RegisterAction
@RegisterSetting(
    key = "browser_restrict_mitigation",
    name = "禁用内置浏览器网页拦截",
    type = SettingType.BOOLEAN,
    desc = "允许在内置浏览器中访问非官方认可的网页。",
    uiTab = "杂项"
)
class BrowserRestrictMitigation : IAction, DexKitTask {

    override fun onRun(ctx: Context, process: ActionProcess) {
        requireMethod("browser_restrict_mitigation").hookBefore {
            val bundle = it.args[0] as Bundle
            if (bundle.getInt("jumpResult", 0) != 0) {
                bundle.putInt("jumpResult", 0)
                bundle.putString("jumpUrl", "")
            }
        }
    }

    override val key: String get() = GeneratedSettingList.BROWSER_RESTRICT_MITIGATION

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "browser_restrict_mitigation" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.webview")
            matcher {
                declaredClass(
                    "com.tencent.mobileqq.webview.WebSecurityPluginV2",
                    StringMatchType.StartsWith
                )
                name = "callback"
                paramTypes = listOf("android.os.Bundle")
                modifiers = Modifier.PUBLIC
            }
        }
    )
}
