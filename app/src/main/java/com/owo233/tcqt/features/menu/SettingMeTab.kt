package com.owo233.tcqt.features.menu

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object SettingMeTab : Feature(
    key = "setting_me_tab",
    name = "转移设置页入口",
    desc = "将抽屉设置页面入口移动到下方我的Tab页面",
    // 排在「长按菜单按钮净化」(1005) 之后
    uiOrder = 1010,
    requires = Requires(minQQVersion = QQVersion.QQ_9_1_75),
) {

    override fun install() {
        "com.tencent.mobileqq.api.impl.DrawerApiImpl".toClass.findMethod {
            name = "needUsedSettingMeTab"
            returnType = boolean
        }.hookBefore { param ->
            param.result = true
        }
    }
}
