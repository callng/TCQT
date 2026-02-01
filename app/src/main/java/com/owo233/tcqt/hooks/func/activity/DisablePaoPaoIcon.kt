package com.owo233.tcqt.hooks.func.activity

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
@RegisterSetting(
    key = "disable_pao_pao_icon",
    name = "禁用泡泡图标",
    type = SettingType.BOOLEAN,
    desc = "将聊天界面中的泡泡图标替换为红包图标。",
    uiTab = "界面"
)
class DisablePaoPaoIcon : IAction {

    @SuppressLint("DiscouragedApi")
    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            "com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout".toHostClass().also { clazz ->
                clazz.findMethod {
                    paramTypes(int, string, null)
                }.hookAfterMethod { param ->
                    /*
                    val layout = param.thisObject as LinearLayout
                    val tags = (0 until layout.childCount).map { idx -> layout.getChildAt(idx).tag }
                    Log.e("PanelIconLinearLayout child tags = $tags")
                    */
                    val layout = param.thisObject as LinearLayout
                    val icon = layout.findViewWithTag<ImageView>(1016)
                    val hbId = HookEnv.hostAppContext.resources
                        .getIdentifier(
                            "qui_red_envelope_aio_oversized_light_selector",
                            "drawable",
                            HookEnv.hostAppPackageName
                        )

                    icon.tag = 1004
                    icon.contentDescription = "红包"
                    icon.setImageResource(hbId)
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_PAO_PAO_ICON
}
