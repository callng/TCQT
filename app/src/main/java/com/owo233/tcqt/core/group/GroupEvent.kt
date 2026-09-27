package com.owo233.tcqt.core.group

/**
 * 群事件：脚本 `onMsg` 之外的另一条事件流。
 *
 * 与消息事件一样，数据源是**服务端推送**（内核 `onMsfPush` / 群成员服务回调），
 * 不是界面状态，因此事件到达即派发。
 */
sealed interface GroupEvent {

    /** 群号。 */
    val groupUin: String

    /** 有成员入群。 */
    data class MemberJoin(
        override val groupUin: String,
        /** 入群者 QQ 号；UID 转换失败时为空串。 */
        val memberUin: String,
        val memberUid: String = "",
    ) : GroupEvent

    /** 有成员退群（含被踢、主动退群）。 */
    data class MemberQuit(
        override val groupUin: String,
        val memberUin: String,
        val memberUid: String = "",
    ) : GroupEvent

    /** 群禁言状态变化。 */
    data class ShutUp(
        override val groupUin: String,
        /** 被禁言者 QQ 号。 */
        val memberUin: String,
        val memberUid: String = "",
        /** 禁言时长（秒）；0 表示解除。 */
        val durationSeconds: Long,
        /** 操作者 QQ 号。 */
        val operatorUin: String = "",
    ) : GroupEvent

    /**
     * 有人拍一拍（或拍我）。
     *
     * 与其它群事件不同，拍一拍也发生在**私聊**里，因此带 [chatType]
     * （1 好友 / 2 群）；群聊时 [groupUin] 才是群号，私聊时它是对方的 QQ 号。
     */
    data class PaiYiPai(
        override val groupUin: String,
        val chatType: Int,
        /** 发起者 QQ 号。 */
        val fromUin: String,
    ) : GroupEvent
}

fun interface GroupEventListener {
    fun onGroupEvent(event: GroupEvent)
}

/**
 * 群事件的进程内分发器。
 *
 * 与 `MessageDispatcher` 同构：注册/注销/派发，单个监听者异常不影响其它监听者。
 */
object GroupEventDispatcher {

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<GroupEventListener>()

    fun register(listener: GroupEventListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun unregister(listener: GroupEventListener) {
        listeners.remove(listener)
    }

    fun dispatch(event: GroupEvent) {
        listeners.forEach { listener ->
            try {
                listener.onGroupEvent(event)
            } catch (t: Throwable) {
                // 监听者异常不能打断其它监听者，也不能打断宿主的推送线程
                runCatching {
                    com.owo233.tcqt.core.log.LogUtils.androidNoFilter
                        .w("群事件监听者异常: $event", t)
                }
            }
        }
    }

    /** 仅测试用：清空注册表，避免用例间互相污染。 */
    internal fun clearForTest() {
        listeners.clear()
    }

    internal val size: Int get() = listeners.size
}
