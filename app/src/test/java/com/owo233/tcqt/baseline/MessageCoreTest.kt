package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.message.MessageDedup
import com.owo233.tcqt.core.message.MessageDispatcher
import com.owo233.tcqt.core.message.MessageEvent
import com.owo233.tcqt.core.message.MessageKind
import com.owo233.tcqt.core.message.MessageListener
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 消息分发链路里**不依赖宿主**的部分：事件投影、广播语义、去重窗口。
 *
 * 真正的 hook 挂载（`onRecvMsg` 混淆类的 DexKit 定位）只能在真机上验证，
 * 由 `MessageCore` 的不过滤日志给出证据。
 */
class MessageCoreTest {

    private val registered = mutableListOf<MessageListener>()

    @AfterTest
    fun tearDown() {
        // 监听器表是进程级单例，跨用例会累积 —— 每个用例跑完彻底清空，
        // 否则「只收到自己那一条」这类断言会因为上一个用例的监听器而失败。
        registered.forEach { MessageDispatcher.unregister(it) }
        registered.clear()
        MessageDispatcher.clearForTest()
    }

    private fun listener(block: (MessageEvent) -> Unit) {
        val listener = MessageListener { block(it) }
        MessageDispatcher.register(listener)
        registered += listener
    }

    private fun record(
        msgId: Long,
        chatType: Int = 2,
        peerUin: Long = 10001,
        peerUid: String = "u_peer",
        senderUin: Long = 20002,
        senderUid: String = "u_sender",
        msgTime: Long = 1_700_000_000,
    ): MsgRecord = MsgRecord().apply {
        this.msgId = msgId
        this.chatType = chatType
        this.peerUin = peerUin
        this.peerUid = peerUid
        this.senderUin = senderUin
        this.senderUid = senderUid
        this.msgTime = msgTime
        this.msgType = 1
    }

    // ── 事件投影 ─────────────────────────────────────────────────────────

    @Test
    fun `事件把宿主字段投影成脚本可用字段`() {
        val event = MessageEvent(MessageKind.RECEIVE, record(msgId = 42))

        assertEquals(MessageKind.RECEIVE, event.kind)
        assertEquals(42L, event.msgId)
        assertEquals(2, event.chatType)
        assertEquals("10001", event.peerUin)
        assertEquals("u_peer", event.peerUid)
        assertEquals("20002", event.senderUin)
        assertEquals("u_sender", event.senderUid)
        assertEquals(1_700_000_000L, event.msgTime)
    }

    @Test
    fun `peerUid 为空时不抛异常`() {
        assertEquals("", MessageEvent(MessageKind.RECEIVE, record(msgId = 1, peerUid = "")).peerUid)
    }

    // ── 广播语义 ─────────────────────────────────────────────────────────

    @Test
    fun `广播把同一条事件发给所有监听器`() {
        val first = mutableListOf<Long>()
        val second = mutableListOf<Long>()
        listener { first += it.msgId }
        listener { second += it.msgId }

        MessageDispatcher.dispatch(MessageEvent(MessageKind.RECEIVE, record(msgId = 7)))

        assertEquals(listOf(7L), first)
        assertEquals(listOf(7L), second)
    }

    @Test
    fun `单个监听器抛异常不影响其它监听器`() {
        val survivors = mutableListOf<Long>()
        listener { throw IllegalStateException("listener boom") }
        listener { survivors += it.msgId }

        MessageDispatcher.dispatch(MessageEvent(MessageKind.RECEIVE, record(msgId = 9)))

        assertEquals(listOf(9L), survivors, "一个监听器抛异常后，后面的监听器必须照常收到")
    }

    @Test
    fun `注销后的监听器不再收到事件`() {
        val received = mutableListOf<Long>()
        val listener = MessageListener { received += it.msgId }
        MessageDispatcher.register(listener)

        MessageDispatcher.dispatch(MessageEvent(MessageKind.RECEIVE, record(msgId = 1)))
        MessageDispatcher.unregister(listener)
        MessageDispatcher.dispatch(MessageEvent(MessageKind.RECEIVE, record(msgId = 2)))

        assertEquals(listOf(1L), received)
    }

    @Test
    fun `本机发送与收到走不同的 kind`() {
        val kinds = mutableListOf<MessageKind>()
        listener { kinds += it.kind }

        MessageDispatcher.dispatch(MessageEvent(MessageKind.RECEIVE, record(msgId = 1)))
        MessageDispatcher.dispatch(MessageEvent(MessageKind.SEND, record(msgId = 2)))

        assertEquals(listOf(MessageKind.RECEIVE, MessageKind.SEND), kinds)
    }

    // ── 去重窗口 ─────────────────────────────────────────────────────────

    @Test
    fun `去重窗口只放行首次出现的 msgId`() {
        val dedup = MessageDedup(limit = 8)

        assertTrue(dedup.tryMark(100), "首次出现应当放行")
        assertFalse(dedup.tryMark(100), "重复出现应当丢弃")
        assertTrue(dedup.tryMark(101), "不同 id 应当放行")
    }

    @Test
    fun `去重窗口超出上限后淘汰最旧的 id`() {
        val dedup = MessageDedup(limit = 3)

        assertTrue(dedup.tryMark(1))
        assertTrue(dedup.tryMark(2))
        assertTrue(dedup.tryMark(3))
        assertTrue(dedup.tryMark(4), "超限后新 id 仍应放行")

        assertEquals(3, dedup.size, "窗口大小必须被限制住，否则长时间运行会持续吃内存")
        assertTrue(dedup.tryMark(4).not(), "刚放行的 id 仍在窗口内")
    }

    @Test
    fun `去重窗口清空后可重新放行`() {
        val dedup = MessageDedup(limit = 4)

        assertTrue(dedup.tryMark(5))
        dedup.clear()
        assertTrue(dedup.tryMark(5), "清空后应当视为首次出现")
    }

    @Test
    fun `默认去重窗口足够大且被显式声明`() {
        assertEquals(
            2048,
            MessageDedup.DEFAULT_LIMIT,
            "默认窗口变小会让「重放的同一条消息」重新被分发，改这个值要有意为之",
        )
    }
}
