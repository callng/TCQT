package com.owo233.tcqt.features.script

import com.owo233.tcqt.features.script.bean.MsgData
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

/**
 * 脚本事件的分发入口：由 [ScriptCore] 挂到宿主管线上。
 *
 * 宿主事件不保证线程、也可能重复触发（如列表重绘），因此：
 * 每个脚本独立兜底，单个脚本报错不影响其它脚本；收到消息按 `msgId` 去重。
 */
internal object ScriptEvents {

    /** `onMsg` 去重窗口，避免列表重绘重复触发。 */
    private const val DEDUP_LIMIT = 512

    private val dispatched = LinkedHashSet<Long>()

    /** 收到的消息：去重后分发给所有运行中脚本的 `onMsg`。 */
    fun onReceiveMessage(record: MsgRecord) {
        val runtimes = ScriptRegistry.runtimes()
        if (runtimes.isEmpty()) return

        if (record.msgId != 0L && !markDispatched(record.msgId)) return

        runtimes.forEach { runtime ->
            // 每个脚本拿到各自的 MsgData：脚本可以改写 msg 而不影响其它脚本
            val data = runCatching { MsgData(record) }.getOrNull() ?: return
            runtime.invoke(ON_MSG, arrayOf(Any::class.java), arrayOf(data))
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

    private fun markDispatched(msgId: Long): Boolean = synchronized(dispatched) {
        if (!dispatched.add(msgId)) return false
        if (dispatched.size > DEDUP_LIMIT) {
            val iterator = dispatched.iterator()
            repeat(dispatched.size - DEDUP_LIMIT) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        true
    }

    const val ON_MSG = "onMsg"
    const val GET_MSG = "getMsg"
    const val UNLOAD = "unLoadPlugin"
}
