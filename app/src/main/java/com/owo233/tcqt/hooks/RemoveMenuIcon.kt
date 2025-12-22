package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.getFields
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.setIntField
import com.tencent.mobileqq.utils.ViewUtils

@RegisterAction
@RegisterSetting(
    key = "remove_menu_icon",
    name = "移除菜单图标",
    type = SettingType.BOOLEAN,
    desc = "移除消息气泡菜单中的图标。",
    uiTab = "界面"
)
class RemoveMenuIcon : IAction{

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout")
            .declaredMethods.first { method ->
                method.returnType == View::class.java && method.paramCount == 4
                        && method.parameterTypes[0] == Int::class.javaPrimitiveType
                        && method.parameterTypes[2] == Boolean::class.javaPrimitiveType
                        && method.parameterTypes[3] == FloatArray::class.java
            }
            .apply {
                hookBeforeMethod { param ->
                    val newVer = param.thisObject.getFields(false).any { it.name == "m" }
                    val defaultHeight = if (newVer) 76f else 71f
                    val scale = 1.5f
                    val height = ViewUtils.dip2px(defaultHeight / scale)
                    param.thisObject.setIntField(if (newVer) "m" else "n", height)
                }
                hookAfterMethod { param ->
                    val root = param.result as LinearLayout
                    if (root.getChildAt(0) is ImageView) {
                        root.removeViewAt(0)
                    }
                }
            }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_MENU_ICON
}
