package com.owo233.tcqt.features.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.loadOrThrow
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.owo233.tcqt.foundation.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "hide_red_pack_skin",
    name = "隐藏红包推荐皮肤",
    type = SettingType.BOOLEAN,
    desc = "隐藏点击红包按钮后出现的红包皮肤推荐。",
    uiTab = "界面"
)
class HideRedPackSkin : IAction{

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            loadOrThrow("com.tencent.mobileqq.qwallet.hb.panel.recommend.SkinRecommendViewModel")
                .declaredMethods
                .single {
                    it.paramCount == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                            && it.parameterTypes[1].name == "kotlin.jvm.functions.Function1"
                }
                .hookBeforeMethod {
                    it.result = Unit
                }
        }
    }

    override val key: String get() = GeneratedSettingList.HIDE_RED_PACK_SKIN
}
