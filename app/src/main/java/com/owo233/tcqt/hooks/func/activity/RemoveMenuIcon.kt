package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getFields
import com.owo233.tcqt.utils.reflect.setObject
import com.tencent.mobileqq.utils.ViewUtils

@RegisterAction
class RemoveMenuIcon : IAction {

    override val name: String get() = "移除菜单图标"
    override val desc: String get() = "移除消息气泡菜单中的图标。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout")
            .findMethod {
                returnType = view
                paramCount = 4
                paramTypes = arrayOf(int, null, boolean, floatArr)
            }
            .apply {
                hookBefore { param ->
                    val newVer = param.thisObject.getFields(false)
                        .any { it.name == "o" && it.type == Int::class.javaPrimitiveType }.not()
                    val defaultHeight = if (newVer) 76f else 71f
                    val scale = 1.5f
                    val height = ViewUtils.dip2px(defaultHeight / scale)
                    param.thisObject.setObject(if (newVer) "m" else "o", height)
                }
                hookAfter { param ->
                    val root = param.result as LinearLayout
                    if (root.getChildAt(0) is ImageView) {
                        root.removeViewAt(0)
                    }
                }
            }
    }

    override val key: String get() = "remove_menu_icon"
}
