package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.widget.LinearLayout
import androidx.core.view.children
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.reflect.MethodUtils

@RegisterAction
@RegisterSetting(
    key = "disable_pao_pao_icon",
    name = "禁用泡泡图标",
    type = SettingType.BOOLEAN,
    desc = "禁止在聊天界面中显示泡泡图标，防止误触。",
    uiTab = "界面"
)
class DisablePaoPaoIcon : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            "com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout".toHostClass().also { clazz ->
                MethodUtils.create(clazz)
                    .params(Int::class.javaPrimitiveType, String::class.java, null)
                    .findOrThrow()
                    .hookAfterMethod { param ->
                        /*
                        val layout = param.thisObject as LinearLayout
                        val tags = (0 until layout.childCount).map { idx -> layout.getChildAt(idx).tag }
                        Log.e("PanelIconLinearLayout child tags = $tags")
                        */
                        (param.thisObject as LinearLayout).run {
                            children
                                .firstOrNull { (it.tag as? Int) == 1016 }
                                ?.let(::removeView)
                        }
                    }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_PAO_PAO_ICON
}
