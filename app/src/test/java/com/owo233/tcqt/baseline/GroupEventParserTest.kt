package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.group.GroupEvent
import com.owo233.tcqt.core.group.GroupEventParser
import com.owo233.tcqt.core.group.ScriptChatType
import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.proto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 群事件推送解析。
 *
 * 字段路径取自宿主推送结构（QQ 9.3.70），用构造出的 protobuf 覆盖，不依赖宿主。
 */
class GroupEventParserTest {

    private val msgPushCmd = "trpc.msg.olpush.OlPushService.MsgPush"

    // ── 禁言 ─────────────────────────────────────────────────────────────

    private fun shutUpPayload(
        groupUin: Long = 709073105L,
        memberUid: String = "u_member",
        operatorUid: String = "u_op",
        duration: Long = 600L,
        headTag2: Long = 12L,
    ): ByteArray {
        val root = ProtoMap()
        root[1, 2] = ProtoMap().apply {
            this[1] = 732L
            this[2] = headTag2
        }
        root[1, 3, 2] = ProtoMap().apply {
            this[1] = groupUin
            this[4] = operatorUid.proto
            this[5, 3] = ProtoMap().apply {
                this[1] = memberUid.proto
                this[2] = duration
            }
        }
        return root.toByteArray()
    }

    @Test
    fun `解析群禁言推送`() {
        val event = GroupEventParser.parsePush(msgPushCmd, shutUpPayload())
        val shutUp = event as? GroupEvent.ShutUp
        assertNotNull(shutUp, "应解析出 ShutUp")
        assertEquals("709073105", shutUp.groupUin)
        assertEquals("u_member", shutUp.memberUid)
        assertEquals(600L, shutUp.durationSeconds)
        assertEquals("u_op", shutUp.operatorUin)
    }

    @Test
    fun `禁言解除时长为 0`() {
        val event = GroupEventParser.parsePush(msgPushCmd, shutUpPayload(duration = 0L))
        assertEquals(0L, (event as GroupEvent.ShutUp).durationSeconds)
    }

    @Test
    fun `消息头不匹配时不当作群禁言`() {
        assertNull(GroupEventParser.parsePush(msgPushCmd, shutUpPayload(headTag2 = 13L)))
    }

    @Test
    fun `非 MsgPush 的 cmd 直接忽略`() {
        assertNull(GroupEventParser.parsePush("trpc.other.Cmd", shutUpPayload()))
    }

    @Test
    fun `乱码字节不会抛异常`() {
        assertNull(GroupEventParser.parsePush(msgPushCmd, byteArrayOf(0x00, 0x01, 0x02)))
    }

    // ── 入群 ─────────────────────────────────────────────────────────────

    private fun memberAddPayload(
        groupUin: Long = 709073105L,
        memberUid: String = "u_new",
    ): ByteArray = ProtoMap().apply {
        this[3, 2] = ProtoMap().apply {
            this[1] = groupUin
            this[3] = memberUid.proto
        }
    }.toByteArray()

    @Test
    fun `解析入群推送`() {
        val join = GroupEventParser.parseMemberAdd(memberAddPayload())
        assertNotNull(join, "应解析出 MemberJoin")
        assertEquals("709073105", join.groupUin)
        assertEquals("u_new", join.memberUid)
    }

    @Test
    fun `入群推送缺群号时返回 null`() {
        val root = ProtoMap()
        root[3, 2, 3] = "u_new".proto
        assertNull(GroupEventParser.parseMemberAdd(root.toByteArray()))
    }

    @Test
    fun `入群乱码字节不抛异常`() {
        assertNull(GroupEventParser.parseMemberAdd(byteArrayOf(0x08)))
    }

    @Test
    fun `禁言解析不会误吞入群推送`() {
        assertNull(GroupEventParser.parsePush(msgPushCmd, memberAddPayload()))
    }

    // ── 拍一拍 ───────────────────────────────────────────────────────────

    /**
     * 群里拍一拍：内容位于 `1.3.2`。
     *
     * 真实推送里值形如 `uin_str1="123",uin_str2="456"`（**逗号分隔**），
     * 内容可能被无描述符解码器解成嵌套消息，两种形态都要能解析。
     */
    private fun groupPaiPayload(
        fromUin: String = "1559921679",
        toUin: String = "1286819801",
        tag2: Long = 20L,
    ): ByteArray {
        // 按真实结构：1.3.2.7 是键值对数组（uin_str1 = 发起者，uin_str2 = 被拍者）。
        // 早期版本把文本放在 1.3.2，造出"测试全绿但真机解析不出来"的假象。
        // 真实字节回归见 PokeRealPayloadTest。
        val kv = com.owo233.tcqt.core.proto.ProtoList()
        kv.add(ProtoMap().apply { this[1] = "uin_str1".proto; this[2] = fromUin.proto })
        kv.add(ProtoMap().apply { this[1] = "uin_str2".proto; this[2] = toUin.proto })
        kv.add(ProtoMap().apply { this[1] = "action_str".proto; this[2] = "戳了戳".proto })

        return ProtoMap().apply {
            this[1, 1, 1] = 709073105L
            this[1, 1, 5] = toUin.toLong()
            this[1, 2] = ProtoMap().apply {
                this[1] = 732L
                this[2] = tag2
            }
            this[1, 3, 2, 7] = kv
        }.toByteArray()
    }

    @Test
    fun `解析群拍一拍`() {
        val event = GroupEventParser.parsePaiYiPai(groupPaiPayload(), selfUin = "1286819801")
        assertNotNull(event, "应解析出 PaiYiPai")
        assertEquals(ScriptChatType.GROUP, event.chatType)
        assertEquals("709073105", event.groupUin)
        assertEquals("1559921679", event.fromUin)
    }

    @Test
    fun `拍的不是我时忽略`() {
        val payload = groupPaiPayload(toUin = "1000000001")
        assertNull(GroupEventParser.parsePaiYiPai(payload, "1286819801"))
    }

    @Test
    fun `消息头不匹配时不是拍一拍`() {
        assertNull(GroupEventParser.parsePaiYiPai(groupPaiPayload(tag2 = 99L), "1286819801"))
    }

    @Test
    fun `拍一拍乱码字节不抛异常`() {
        assertNull(GroupEventParser.parsePaiYiPai(byteArrayOf(0x08, 0x01), selfUin = "1"))
    }

    @Test
    fun `禁言解析不会误吞拍一拍`() {
        assertNull(GroupEventParser.parseShutUp(groupPaiPayload()))
    }
}