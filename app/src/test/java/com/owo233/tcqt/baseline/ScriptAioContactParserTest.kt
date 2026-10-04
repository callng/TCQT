package com.owo233.tcqt.baseline

import com.owo233.tcqt.features.script.ScriptAioContactParser
import com.owo233.tcqt.features.script.ScriptContactResolver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AIO 会话解析。
 *
 * 字段语义来自宿主证据（QQ 9.3.70 的 `AIOContact.toString()` 逐字段拼
 * `AIOContact(chatType=…, peerUid='…', guildId='…', nick='…')`），字段名每版都可能变，
 * 因此解析只依赖**类型与声明顺序**。
 */
class ScriptAioContactParserTest {

    @AfterTest
    fun tearDown() {
        ScriptContactResolver.converter = null
    }

    // ── 字符串形态解析 ───────────────────────────────────────────────────

    @Test
    fun `解析群聊会话串`() {
        val context = ScriptAioContactParser.parseContactString(
            "AIOContact(chatType=2, peerUid='709073105', guildId='', nick='测试群')"
        )

        assertEquals(2, context.chatType)
        assertEquals("709073105", context.peerUid)
        assertEquals("709073105", context.peerUin, "群聊 peerUid 即群号")
        assertEquals("测试群", context.peerName)
    }

    @Test
    fun `解析好友会话串时用转换器把 uid 换成 uin`() {
        ScriptContactResolver.converter = { uid -> if (uid == "u_abc") "123456" else "" }

        val context = ScriptAioContactParser.parseContactString(
            "AIOContact(chatType=1, peerUid='u_abc', guildId='', nick='某人')"
        )

        assertEquals(1, context.chatType)
        assertEquals("u_abc", context.peerUid)
        assertEquals("123456", context.peerUin)
    }

    @Test
    fun `转换不出 uin 时退回兜底值`() {
        ScriptContactResolver.converter = { "" }

        val context = ScriptAioContactParser.parseContactString(
            "AIOContact(chatType=1, peerUid='u_abc', guildId='', nick='')",
            fallbackUin = "999",
        )

        assertEquals("999", context.peerUin)
    }

    @Test
    fun `无法识别的串得到空会话`() {
        val context = ScriptAioContactParser.parseContactString("garbage")
        assertEquals(0, context.chatType)
        assertEquals(false, context.isValid)
    }

    // ── 字段直读 ─────────────────────────────────────────────────────────

    /** 结构等价于宿主 AIOContact：1 个 int + 3 个 String（顺序即语义顺序）。 */
    @Suppress("unused")
    private class FakeAioContact(
        private val a: Int,
        private val b: String,
        private val c: String,
        private val d: String,
    ) {
        override fun toString(): String =
            "AIOContact(chatType=$a, peerUid='$b', guildId='$c', nick='$d')"
    }

    @Test
    fun `字段直读优先于字符串解析`() {
        // 名字故意用 a/b/c/d，证明解析不依赖字段名
        val fake = FakeAioContact(2, "709073105", "", "测试群")
        val context = ScriptAioContactParser.fromContactObject(fake, fallbackUin = "")

        assertEquals(2, context.chatType)
        assertEquals("709073105", context.peerUid)
        assertEquals("709073105", context.peerUin)
        assertEquals("测试群", context.peerName)
    }

    @Test
    fun `字段直读能处理好友会话`() {
        ScriptContactResolver.converter = { uid -> if (uid == "u_x") "888" else "" }

        val fake = FakeAioContact(1, "u_x", "", "某人")
        val context = ScriptAioContactParser.fromContactObject(fake, fallbackUin = "")

        assertEquals(1, context.chatType)
        assertEquals("888", context.peerUin)
    }
}
