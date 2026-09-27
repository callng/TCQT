package com.owo233.tcqt.features.script

/**
 * 脚本消息文本里的富文本语法：`[atUin=QQ号]` / `[pic=路径或URL]`。
 *
 * 解析是**纯函数**、与宿主无关，因此可以直接单测；真正生成宿主元素的是
 * [ScriptElementFactory]。
 *
 * - `[atUin=123456]` 艾特某人；`[atUin=0]` 艾特全体（仅群聊有效）
 * - `[pic=/sdcard/a.jpg]` 发图片；给 URL 会先下载到缓存目录
 */
internal object ScriptRichText {

    const val CHAT_GROUP = 2

    /** 一个片段的类型。 */
    enum class PartKind { TEXT, AT, PIC }

    data class Part(val kind: PartKind, val value: String)

    private val SLOT = Regex("\\[atUin=\\d+]|\\[pic=.*?]")
    private val SLOT_VALUE = Regex("\\[(atUin|pic)=([^]]*)]")

    /**
     * 把整段文本切成「纯文本 / 艾特 / 图片」片段序列。
     *
     * 不认识的 `[...]` 原样当作文本，不会被吞掉。
     */
    fun parse(input: String): List<Part> {
        if (input.isEmpty()) return emptyList()

        val parts = mutableListOf<Part>()
        var lastEnd = 0

        SLOT.findAll(input).forEach { match ->
            if (match.range.first > lastEnd) {
                parts += Part(PartKind.TEXT, input.substring(lastEnd, match.range.first))
            }
            parts += toPart(match.value)
            lastEnd = match.range.last + 1
        }

        if (lastEnd < input.length) {
            parts += Part(PartKind.TEXT, input.substring(lastEnd))
        }
        return parts
    }

    private fun toPart(slot: String): Part {
        val groups = SLOT_VALUE.matchEntire(slot)?.groupValues
            ?: return Part(PartKind.TEXT, slot)

        return when (groups[1]) {
            "atUin" -> Part(PartKind.AT, groups[2])
            "pic" -> Part(PartKind.PIC, groups[2])
            else -> Part(PartKind.TEXT, slot)
        }
    }

    /** 文本里是否含任一富文本片段。 */
    fun hasSlot(input: String): Boolean = SLOT.containsMatchIn(input)

    /**
     * 图片路径解析：本地文件直接用，URL 先下载。
     *
     * @return 可用的本地路径；下载失败或文件不存在时返回 null
     */
    fun resolvePicPath(raw: String): String? {
        if (raw.startsWith("http", ignoreCase = true)) {
            return ScriptImageDownloader.download(raw)
        }
        return raw.takeIf { java.io.File(it).exists() }
    }
}
