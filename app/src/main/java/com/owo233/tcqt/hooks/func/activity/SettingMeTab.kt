package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class SettingMeTab : IAction {

    override val name: String get() = "转移设置页入口"
    override val desc: String get() = "将抽屉设置页面入口移动到下方我的Tab页面"
    override val uiTab: String get() = "界面"
    override val key: String
        get() = "setting_me_tab"

    override fun onRun(app: Application, process: ActionProcess) {
        if (requireMinQQVersion(QQVersion.QQ_9_1_75)) {
            "com.tencent.mobileqq.api.impl.DrawerApiImpl".toClass.findMethod {
                name = "needUsedSettingMeTab"
                returnType = boolean
            }.hookBefore { param ->
                param.result = true
            }
        }
    }
}
