package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.group.RKeyCollector
import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.proto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * rkey 响应解析。
 *
 * 结构：`4.4.1` 是数组，第 0 项好友 rkey、第 1 项群 rkey，值在各自 `1` 字段。
 */
class RKeyCollectorTest {

    private fun payload(
        friend: String = "friend_rkey_value",
        group: String = "group_rkey_value",
    ): ByteArray {
        val arr = com.owo233.tcqt.core.proto.ProtoList()
        arr.add(ProtoMap().apply { this[1] = friend.proto })
        arr.add(ProtoMap().apply { this[1] = group.proto })
        return ProtoMap().apply {
            this[4, 4, 1] = arr
        }.toByteArray()
    }

    @Test
    fun `解析出好友与群 rkey`() {
        val parsed = RKeyCollector.parse(payload())
        assertNotNull(parsed, "应解析出 rkey")
        assertEquals("friend_rkey_value", parsed.first)
        assertEquals("group_rkey_value", parsed.second)
    }

    @Test
    fun `数组不足两项时返回 null`() {
        val arr = com.owo233.tcqt.core.proto.ProtoList()
        arr.add(ProtoMap().apply { this[1] = "only_friend".proto })
        val bytes = ProtoMap().apply { this[4, 4, 1] = arr }.toByteArray()
        assertNull(RKeyCollector.parse(bytes))
    }

    @Test
    fun `结构不对时返回 null`() {
        val bytes = ProtoMap().apply { this[9] = 1L }.toByteArray()
        assertNull(RKeyCollector.parse(bytes))
    }

    @Test
    fun `乱码字节不抛异常`() {
        assertNull(RKeyCollector.parse(byteArrayOf(0x00, 0x01)))
    }

    @Test
    fun `空 rkey 不覆盖已有值`() {
        RKeyCollector.resetForTest()
        RKeyCollector.update("f1", "g1")
        RKeyCollector.update("", "")
        assertEquals("f1", RKeyCollector.friendRkey)
        assertEquals("g1", RKeyCollector.groupRkey)
    }

    @Test
    fun `非空 rkey 覆盖旧值`() {
        RKeyCollector.resetForTest()
        RKeyCollector.update("f1", "g1")
        RKeyCollector.update("f2", "g2")
        assertEquals("f2", RKeyCollector.friendRkey)
        assertEquals("g2", RKeyCollector.groupRkey)
        RKeyCollector.resetForTest()
    }
}