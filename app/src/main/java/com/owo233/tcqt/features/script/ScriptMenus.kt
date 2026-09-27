package com.owo233.tcqt.features.script

import com.owo233.tcqt.R
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.service.CustomMenu
import java.util.concurrent.atomic.AtomicInteger

/**
 * 脚本菜单项构造：复用模块既有的 [CustomMenu]（NT 长按菜单的宿主工厂）。
 */
internal object ScriptMenus {

    /** 脚本菜单 id 段，避开模块内置菜单项占用的 id。 */
    private const val ID_BASE = 0x7F0F0000

    private val idGenerator = AtomicInteger(ID_BASE)

    fun createMessageMenuItem(msgItem: Any, title: String, click: () -> Unit): Any? =
        runCatching {
            CustomMenu.createItemIconNt(
                msg = msgItem,
                text = title,
                icon = R.drawable.ic_action_recall,
                id = idGenerator.incrementAndGet(),
                click = click,
            )
        }.onFailure {
            Log.e("脚本菜单项创建失败: $title", it)
        }.getOrNull()
}
