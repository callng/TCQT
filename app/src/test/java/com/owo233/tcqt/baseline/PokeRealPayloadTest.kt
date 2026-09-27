package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.group.GroupEventParser
import com.owo233.tcqt.core.group.ScriptChatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 拍一拍解析 —— **用真机抓包的真实推送字节**做回归。
 *
 * 之前的测试用的是我自己构造的合成 payload，格式与真实数据不符（合成里 `1.3.2`
 * 是文本，真实里 `1.3.2` 是子消息，数据在 `1.3.2.7` 的键值对数组里），
 * 所以合成数据全绿而真实数据一条都解析不出来。
 *
 * 这份 hex 是用户在 QQ 9.3.70 上抓的群拍一拍推送，`trpc.msg.olpush.OlPushService.MsgPush`。
 * 解析字段值（解码核对）：
 * - `1.1.1`   = 1108876052（群号）
 * - `1.1.5`   = 1286819801（被拍者）
 * - `1.2.1/2` = 732 / 20
 * - `1.3.2.7` = [uin_str1=1559921679, uin_str2=1286819801, action_str=戳了戳, …]
 */
class PokeRealPayloadTest {

    private val groupPokeHex =
        "0000033f0a83060a200894b6e09004120a3131303838373630353228d99fcde5044206420438d0800a12350" +
            "8dc051014181420b687bcd50428bced0130feb2e6d50660b687bcd58480808002e8019e01f8019e0180028b9d" +
            "f4b781b7b6baf9011aa7050a0012a20542181b1414029b08142094b6e090046813d201ee04080c10a508180730" +
            "ec083a0d0a096e69636b5f7374723112003a0d0a096e69636b5f7374723212003a160a0875696e5f7374723112" +
            "0a313535393932313637393a160a0875696e5f73747232120a313238363831393830313a0e0a09747970655f73" +
            "7472321201303a0e0a0a7375666669785f73747212003a170a0a616374696f6e5f7374721209e688b3e4ba86e6" +
            "88b33a4c0a0e616374696f6e5f696d675f75726c123a687474703a2f2f7469616e7175616e2e6774696d672e63" +
            "6e2f6e75646765616374696f6e2f6974656d2f302f65787072657373696f6e2e6a70673a480a076a705f737472" +
            "31123d68747470733a2f2f7a622e7669702e71712e636f6d2f76322f70616765732f6e756467654d616c6c3f5f" +
            "77763d3226616d703b616374696f6e49643d3042bc023c6774697020616c69676e3d2263656e746572223e203c" +
            "71712075696e3d22755f6b2d626a44564e5345426f504e505a5f5638324130412220636f6c3d223122206e6d3d" +
            "2222202f3e203c696d67207372633d22687474703a2f2f7469616e7175616e2e6774696d672e636e2f6e756467" +
            "65616374696f6e2f6974656d2f302f65787072657373696f6e2e6a706722206a703d2268747470733a2f2f7a62" +
            "2e7669702e71712e636f6d2f76322f70616765732f6e756467654d616c6c3f5f77763d3226616d703b61637469" +
            "6f6e49643d3022202f3e203c6e6f72207478743d22e688b3e4ba86e688b3222f3e203c71712075696e3d22755f" +
            "4c4b6b4c4349304666386e5654385a31557631725f512220636f6c3d223122206e6d3d22222074703d2230222f" +
            "3e203c6e6f72207478743d22222f3e203c2f677469703e50b687bcd504a8029e01b802bdf6b84180039e018803" +
            "009003b687bcd504b003b2b1e6d506180122310a0c392e3134382e3231392e373510fe9d011a1d10dc0518bced" +
            "0120b687bcd58480808002301438014094b6e090044801"

    private val selfUin = "1286819801"

    private fun bytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
        }

    @Test
    fun `真实群拍一拍推送能解析出事件`() {
        val event = GroupEventParser.parsePaiYiPai(bytes(groupPokeHex), selfUin)
        assertNotNull(event, "真实群拍一拍必须能解析出来")
        assertEquals(ScriptChatType.GROUP, event.chatType)
        assertEquals("1108876052", event.groupUin)
        assertEquals("1559921679", event.fromUin)
    }

    @Test
    fun `带 4 字节长度前缀也能解析`() {
        // 这份数据前面是 0000033f = 831 = 总长度，解码时必须剥掉
        val raw = bytes(groupPokeHex)
        assertEquals(831, raw.size, "测试数据长度应与前缀一致")
        assertNotNull(GroupEventParser.parsePaiYiPai(raw, selfUin))
    }

    @Test
    fun `拍的不是我时不派发`() {
        assertNull(GroupEventParser.parsePaiYiPai(bytes(groupPokeHex), "999999999"))
    }

    @Test
    fun `不传 selfUin 时不做拍我过滤`() {
        assertNotNull(GroupEventParser.parsePaiYiPai(bytes(groupPokeHex), ""))
    }

    @Test
    fun `真实群拍一拍不会再被禁言解析误吞`() {
        assertNull(GroupEventParser.parseShutUp(bytes(groupPokeHex)))
    }
}
