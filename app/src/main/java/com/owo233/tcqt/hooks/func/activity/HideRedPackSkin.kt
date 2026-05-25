package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
class HideRedPackSkin : IAction {

    override val name: String get() = "隐藏红包推荐皮肤"
    override val desc: String get() = "隐藏点击红包按钮后出现的红包皮肤推荐。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            loadOrThrow("com.tencent.mobileqq.qwallet.hb.panel.recommend.SkinRecommendViewModel")
                .declaredMethods
                .single {
                    it.paramCount == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                            && it.parameterTypes[1].name == "kotlin.jvm.functions.Function1"
                }
                .hookBefore {
                    it.result = Unit
                }
        }
    }

    override val key: String get() = "hide_red_pack_skin"
}
