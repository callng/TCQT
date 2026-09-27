package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.group.GroupEvent
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

    /**
     * 进入聊天界面：分发 `chatInterface`。
     *
     * 文档约定回调有两种签名（3 参 / 4 参含 Contact），因此按方法的参数个数匹配，
     * 而不是猜一个固定签名。
     */
    fun onChatInterface(contact: ScriptChatContext) {
        if (!contact.isValid) return

        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return

        runtimes.forEach { runtime ->
            runtime.invokeByNameSize(
                name = CHAT_INTERFACE,
                sizes = intArrayOf(3, 4),
                buildArgs = { size ->
                    if (size == 4) {
                        arrayOf(contact.chatType, contact.peerUin, contact.peerName, contact.toKernelContact())
                    } else {
                        arrayOf(contact.chatType, contact.peerUin, contact.peerName)
                    }
                },
            )
        }
    }

    /**
     * 群事件：入群 / 退群 / 禁言。
     *
     * 文档约定回调为 `(String 群号, String 成员QQ号[, 附加参数...])`，这里统一按
     * 参数个数匹配，兼容脚本少写末尾参数的情况。
     */
    fun onGroupEvent(event: GroupEvent) {
        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return

        val (name, args) = when (event) {
            is GroupEvent.MemberJoin -> JOIN_GROUP to arrayOf<Any?>(event.groupUin, event.memberUin)
            is GroupEvent.MemberQuit -> QUIT_GROUP to arrayOf<Any?>(event.groupUin, event.memberUin)
            is GroupEvent.ShutUp -> SHUT_UP_GROUP to arrayOf<Any?>(
                event.groupUin,
                event.memberUin,
                event.durationSeconds,
                event.operatorUin,
            )

            // 拍一拍也发生在私聊，因此回调带上 chatType
            is GroupEvent.PaiYiPai -> PAI_YI_PAI to arrayOf<Any?>(
                event.groupUin,
                event.chatType,
                event.fromUin,
            )
        }

        runtimes.forEach { runtime ->
            runtime.invokeByNameSize(name, intArrayOf(args.size, args.size - 1)) { size ->
                args.copyOf(size)
            }
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
    const val CHAT_INTERFACE = "chatInterface"
    const val JOIN_GROUP = "joinGroup"
    const val QUIT_GROUP = "quitGroup"
    const val SHUT_UP_GROUP = "shutUpGroup"
    const val PAI_YI_PAI = "onPaiYiPai"
}
