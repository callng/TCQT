package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class HideGuildAD : IAction {

    override val key: String get() = "hide_guild_ad"
    override val name: String get() = "隐藏频道广告"
    override val desc: String get() = "隐藏频道中的广告，或许还能隐藏一些其他广告。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.gdtad.aditem.GdtAd".toClass.findMethod {
            name = "isValid"
            returnType = boolean
        }.hookBefore {
            it.result = false
        }
    }
}
