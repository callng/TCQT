package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
@RegisterSetting(
    key = "setting_me_tab",
    name = "转移设置页入口",
    type = SettingType.BOOLEAN,
    desc = "将抽屉设置页面入口移动到下方我的Tab页面",
    hasTextAreas = true,
    uiTab = "界面"
)
class SettingMeTab : IAction {

    override val key: String
        get() = GeneratedSettingList.SETTING_ME_TAB

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
