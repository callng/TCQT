package com.owo233.tcqt.baseline

import com.owo233.tcqt.features.script.ScriptRichText
import com.owo233.tcqt.features.script.ScriptRichText.PartKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 脚本富文本语法 `[atUin=]` / `[pic=]` 的解析。
 *
 * 这是纯函数，与宿主无关，因此可以完整单测；真正生成宿主元素的是
 * `ScriptElementFactory`（只能在真机验证）。
 */
class ScriptRichTextTest {

    private fun parse(input: String) = ScriptRichText.parse(input)

    @Test
    fun `纯文本切成单个 text 片段`() {
        assertEquals(
            listOf(ScriptRichText.Part(PartKind.TEXT, "你好")),
            parse("你好"),
        )
    }

    @Test
    fun `艾特片段被识别，前后的文本各自成段`() {
        assertEquals(
            listOf(
                ScriptRichText.Part(PartKind.TEXT, "你好"),
                ScriptRichText.Part(PartKind.AT, "123456"),
                ScriptRichText.Part(PartKind.TEXT, " 在吗"),
            ),
            parse("你好[atUin=123456] 在吗"),
        )
    }

    @Test
    fun `全体艾特用 atUin=0 表示`() {
        assertEquals(
            listOf(ScriptRichText.Part(PartKind.AT, "0")),
            parse("[atUin=0]"),
        )
    }

    @Test
    fun `图片片段被识别且保留完整 URL`() {
        val url = "https://multimedia.nt.qq.com.cn/download?appid=1407&fileid=abc"
        assertEquals(
            listOf(
                ScriptRichText.Part(PartKind.TEXT, "看图 "),
                ScriptRichText.Part(PartKind.PIC, url),
            ),
            parse("看图 [pic=$url]"),
        )
    }

    @Test
    fun `多个片段按顺序切分`() {
        assertEquals(
            listOf(
                ScriptRichText.Part(PartKind.AT, "1"),
                ScriptRichText.Part(PartKind.TEXT, " 和 "),
                ScriptRichText.Part(PartKind.AT, "2"),
            ),
            parse("[atUin=1] 和 [atUin=2]"),
        )
    }

    @Test
    fun `不认识的方括号原样当文本，不被吞掉`() {
        assertEquals(
            listOf(ScriptRichText.Part(PartKind.TEXT, "[b]粗体[/b]")),
            parse("[b]粗体[/b]"),
        )
    }

    @Test
    fun `atUin 非数字不匹配，按文本处理`() {
        assertEquals(
            listOf(ScriptRichText.Part(PartKind.TEXT, "[atUin=abc]")),
            parse("[atUin=abc]"),
        )
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(parse("").isEmpty())
    }

    @Test
    fun `片段紧跟结尾时不产生空文本段`() {
        assertEquals(
            listOf(
                ScriptRichText.Part(PartKind.TEXT, "a"),
                ScriptRichText.Part(PartKind.AT, "9"),
            ),
            parse("a[atUin=9]"),
        )
    }

    @Test
    fun `hasSlot 能区分纯文本与含片段`() {
        assertTrue(ScriptRichText.hasSlot("x[atUin=1]"))
        assertTrue(ScriptRichText.hasSlot("[pic=/a.jpg]"))
        assertFalse(ScriptRichText.hasSlot("普通文本 [b]x[/b]"))
    }

    @Test
    fun `本地图片路径解析只接受存在的文件`() {
        // 不存在的路径返回 null（由调用方降级成提示文本）
        assertEquals(null, ScriptRichText.resolvePicPath("/definitely/not/here.jpg"))
    }

    @Test
    fun `群聊常量与文档一致`() {
        assertEquals(2, ScriptRichText.CHAT_GROUP, "群聊 chatType 必须是 2，atUin 只在群聊生效")
    }
}
