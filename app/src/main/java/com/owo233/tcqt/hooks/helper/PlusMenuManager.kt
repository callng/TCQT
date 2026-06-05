package com.owo233.tcqt.hooks.helper

import android.app.Activity
import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.reflect.getObject
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.util.concurrent.CopyOnWriteArrayList

@RegisterAction
object PlusMenuManager : AlwaysRunAction(), DexKitTask {

    override val key: String get() = "plus_menu_manager"

    private val items = CopyOnWriteArrayList<ExtraMenuItem>()

    fun register(item: ExtraMenuItem) {
        items.add(item)
    }

    fun registerAll(vararg menuItems: ExtraMenuItem) {
        items.addAll(menuItems)
    }

    private fun buildMenuItems(): List<Any> {
        val clazz = loadOrThrow($$"com.tencent.widget.PopupMenuDialog$MenuItem")
        return items.sortedBy { it.id }.map { item ->
            clazz.getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(item.id, item.title, item.title, item.iconResId)
        }
    }

    override fun onRun(app: Application, process: ActionProcess) {
        hookBuild()
        hookClick()
    }

    private fun hookBuild() {
        loadOrThrow("com.tencent.widget.PopupMenuDialog")
            .hookMethodBefore(
                "conversationPlusBuild",
                Activity::class.java,
                List::class.java,
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener"),
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnDismissListener")
            ) { param ->
                val activity = param.args[0] as Activity
                ResourcesUtils.injectResourcesToContext(activity.resources)
                param.args[1] = (param.args[1] as List<*>) + buildMenuItems()
            }
    }

    private fun hookClick() {
        requireMethod("AddPlusMenu").hookBefore { param ->
            val clickedId = param.args[0]!!.getObject("id") as Int
            items.find { it.id == clickedId }?.let {
                it.onClick()
                param.result = Unit
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "AddPlusMenu" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.activity.recent")
            matcher {
                name = "onClickAction"
                paramTypes($$"com.tencent.widget.PopupMenuDialog$MenuItem")
                declaredClass {
                    addInterface($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener")
                }
            }
        }
    )
}

data class ExtraMenuItem(
    val id: Int,
    val title: String,
    val iconResId: Int,
    val onClick: () -> Unit
)
