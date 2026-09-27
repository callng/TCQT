package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.message.MessageEvent
import com.owo233.tcqt.core.message.MessageKind
import com.owo233.tcqt.features.script.bean.MsgData
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

/**
 * 脚本事件的分发入口。
 *
 * 消息类事件由 [com.owo233.tcqt.core.message.MessageCore] 从**服务端推送入口**
 * （`msgService.onRecvMsg` / `onAddSendMsg`）广播过来，而不是视图管线 ——
 * 视图只在用户真的看到消息时更新，拿它当消息源会漏掉后台消息。
 *
 * 每个脚本独立兜底，单个脚本报错不影响其它脚本。
 */
internal object ScriptEvents {

    /**
     * 服务端推送的消息：分发给所有运行中脚本的 `onMsg`。
     *
     * 「本机发送」走 [onSentMessage]，不混进 `onMsg`：否则脚本回复消息会再次触发
     * `onMsg`，形成自回环。
     */
    fun onReceiveMessage(event: MessageEvent) {
        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return

        when (event.kind) {
            MessageKind.RECEIVE -> dispatchToScripts(runtimes, ON_MSG, event.record)
            MessageKind.SEND -> dispatchToScripts(runtimes, ON_SEND_MSG, event.record)
        }
    }

    /** 发送前改写文本：返回替换后的文本，未命中返回 null。 */
    fun rewriteText(element: MsgElement): String? {
        val original = element.textElement?.content ?: return null

        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return null

        var current = original
        runtimes.forEach { runtime ->
            if (!runtime.hasMethod(GET_MSG)) return@forEach
            runtime.invokeForString(GET_MSG, current)?.let { current = it }
        }

        return current.takeIf { it != original }
    }

    /** 消息长按菜单构建完成：追加脚本注册的菜单项。 */
    fun onBuildMenu(msgRecord: MsgRecord, msgItem: Any, menuList: List<Any?>): List<Any?> {
        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return menuList

        var result = menuList
        runtimes.forEach { runtime ->
            runtime.msgMenuItems.forEach { (name, callback) ->
                val item = ScriptMenus.createMessageMenuItem(msgItem, name) {
                    val data = runCatching { MsgData(msgRecord) }.getOrNull()
                        ?: return@createMessageMenuItem
                    runtime.invoke(callback, arrayOf(Any::class.java), arrayOf(data))
                } ?: return@forEach

                result = listOf(item) + result
            }
        }
        return result
    }

    /**
     * 每个脚本拿到**各自**的 `MsgData`：脚本可以改写 `msg` 而不影响其它脚本。
     * 回调方法不存在时 [ScriptRuntime.invoke] 静默返回 false。
     */
    private fun dispatchToScripts(
        runtimes: List<ScriptRuntime>,
        callback: String,
        record: MsgRecord,
    ) {
        runtimes.forEach { runtime ->
            val data = runCatching { MsgData(record) }.getOrNull() ?: return@forEach
            runtime.invoke(callback, arrayOf(Any::class.java), arrayOf(data))
        }
    }

    const val ON_MSG = "onMsg"
    const val ON_SEND_MSG = "onSendMsg"
    const val GET_MSG = "getMsg"
    const val UNLOAD = "unLoadPlugin"
}
