package com.owo233.tcqt.features.script

import com.owo233.tcqt.R
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.service.CustomMenu
import java.util.concurrent.atomic.AtomicInteger

/**
 * 脚本菜单：两类入口共用一处构造与派发。
 *
 * - **消息长按菜单**：`addMenuItem` 注册，走宿主 [CustomMenu]；
 * - **悬浮菜单**：`addItem` 注册，由 [ScriptMenuView] 消费。
 */
internal object ScriptMenus {

    /** 脚本菜单 id 段，避开模块内置菜单项占用的 id。 */
    private const val ID_BASE = 0x7F0F0000

    private val idGenerator = AtomicInteger(ID_BASE)

    // ── 消息长按菜单 ─────────────────────────────────────────────────────

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
            LogUtils.androidNoFilter.e("脚本消息菜单创建失败: $title", it)
        }.getOrNull()

    // ── 悬浮菜单 ─────────────────────────────────────────────────────────

    /**
     * 汇总所有运行中脚本注册的 `addItem`。
     *
     * 没有脚本注册菜单时返回空列表，[ScriptMenuView] 据此不显示悬浮球 ——
     * 没脚本的时候不该在聊天界面留一个点了没反应的球。
     */
    fun buildItems(): List<ScriptMenuItem> {
        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return emptyList()

        val items = mutableListOf<ScriptMenuItem>()
        runtimes.forEach { runtime ->
            if (runtime.menuItems.isEmpty()) return@forEach

            items += ScriptMenuItem.Header(runtime.info.name)
            runtime.menuItems.forEach { (title, callback) ->
                items += ScriptMenuItem.Action(runtime.info.id, title, callback)
            }
        }
        return items
    }

    /** 派发悬浮菜单点击：按脚本 id 找运行实例并调用它的回调方法。 */
    fun invoke(item: ScriptMenuItem.Action) {
        val runtime = ScriptRegistry.runtimes().firstOrNull { it.info.id == item.scriptId }
        if (runtime == null) {
            LogUtils.androidNoFilter.w("脚本菜单点击: 脚本未运行 ${item.scriptId}")
            return
        }
        runtime.invokeMenuItem(item.callback, ScriptAioEntry.currentContact)
    }

    /** 当前是否有任何脚本注册了悬浮菜单（决定要不要挂球）。 */
    fun hasAnyItem(): Boolean = ScriptRegistry.runtimes().any { it.menuItems.isNotEmpty() }
}
