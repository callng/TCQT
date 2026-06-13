package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.isNotStatic
import com.owo233.tcqt.utils.reflect.findMethod
import java.util.WeakHashMap

@RegisterAction
class RemoveMenuIcon : IAction {

    override val key: String get() = "remove_menu_icon"
    override val name: String get() = "移除菜单图标"
    override val desc: String get() = "移除消息气泡菜单中的图标。"
    override val uiTab: String get() = "界面"

    private val scaledObjects = WeakHashMap<Any, Boolean>()

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout".toClass.findMethod {
            returnType = view
            paramCount = 4
            paramTypes = arrayOf(int, null, boolean, floatArr)
        }.apply {
            hookBefore { param ->
                val layout = param.thisObject
                if (scaledObjects.containsKey(layout)) return@hookBefore

                val fields = layout.javaClass.declaredFields
                    .filter { it.type == Int::class.javaPrimitiveType && it.isNotStatic }
                    .onEach { it.isAccessible = true }

                val rowHeightField = fields.firstOrNull { it.name == "m" || it.name == "o" }
                    ?: fields.maxByOrNull { it.getInt(layout) }

                if (rowHeightField != null) {
                    val currentVal = rowHeightField.getInt(layout)
                    if (currentVal > 0) {
                        val scaledVal = (currentVal / 1.5f).toInt()
                        rowHeightField.setInt(layout, scaledVal)
                        scaledObjects[layout] = true
                    }
                }
            }
            hookAfter { param ->
                val root = param.result as? ViewGroup ?: return@hookAfter
                fun hideFirstImageView(viewGroup: ViewGroup): Boolean {
                    for (i in 0 until viewGroup.childCount) {
                        val child = viewGroup.getChildAt(i)
                        if (child is ImageView) {
                            child.visibility = View.GONE
                            child.layoutParams?.let { lp ->
                                if (lp is ViewGroup.MarginLayoutParams) {
                                    lp.setMargins(0, 0, 0, 0)
                                }
                                lp.width = 0
                                lp.height = 0
                                child.layoutParams = lp
                            }
                            return true
                        } else if (child is ViewGroup) {
                            if (hideFirstImageView(child)) return true
                        }
                    }
                    return false
                }
                hideFirstImageView(root)
            }
        }
    }
}
