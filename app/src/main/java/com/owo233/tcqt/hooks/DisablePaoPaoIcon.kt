package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookBeforeMethod

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
            loadOrThrow("com.tencent.qqnt.aio.filtervideo.api.impl.FilterVideoApiImpl")
                .hookBeforeMethod("isEnable") { param ->
                    param.result = false
                }
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_PAO_PAO_ICON
}
