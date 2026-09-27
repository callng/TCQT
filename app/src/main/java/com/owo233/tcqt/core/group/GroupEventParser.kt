package com.owo233.tcqt.core.group

import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.proto.ProtoDecodeMode
import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.ProtoUtils
import com.owo233.tcqt.core.proto.asLong
import com.owo233.tcqt.core.proto.asMap
import com.owo233.tcqt.core.proto.asList
import com.owo233.tcqt.core.proto.asString

/**
 * 群事件的原生解析：只做「字节 → [GroupEvent]」，不涉及 hook。
 *
 * 因此可以脱离宿主单测（本文件在 `GroupEventParserTest` 里用构造的 protobuf 覆盖）。
 *
 * 字段路径来自宿主推送结构（QQ 9.3.70）：
 * - **群禁言**：`OlPushService.MsgPush`，消息头 `1.2` = (732, 12)，
 *   群信息 `1.3.2`，被禁言 `1.3.2.5.3`（`1` = 目标 UID，`2` = 时长），
 *   操作者 `1.3.2.4`；
 * - **入群**：`TroopMemberAddPushProcessor`，群号 `3.2.1`，入群者 UID `3.2.3`（UID 需另行转换）。
 */
object GroupEventParser {

    /** `OlPushService.MsgPush` 里群禁言推送的消息头取值。 */
    const val SHUT_UP_HEAD_TAG_1 = 732
    const val SHUT_UP_HEAD_TAG_2 = 12

    /** 拍一拍的消息头：群聊 732/20，私聊 528/290。 */
    const val PAI_GROUP_TAG_1 = 732
    const val PAI_GROUP_TAG_2 = 20
    const val PAI_FRIEND_TAG_1 = 528
    const val PAI_FRIEND_TAG_2 = 290

    private const val MSG_PUSH_CMD = "trpc.msg.olpush.OlPushService.MsgPush"

    private val QQ_PATTERN = Regex("^[1-9]\\d{4,12}$")

    /**
     * 从内核推送解析群事件。
     *
     * @param command 推送的 serviceCmd
     * @param payload 推送的原始字节
     * @param selfUin 本机 QQ 号；拍一拍要按它过滤「拍我」
     * @return 解析出的事件；不是群事件时返回 null（不抛异常）
     */
    fun parsePush(command: String, payload: ByteArray, selfUin: String = ""): GroupEvent? {
        if (command != MSG_PUSH_CMD) return null

        return runCatching { parseShutUp(payload) }
            .onFailure { LogUtils.androidNoFilter.w("群事件: 禁言推送解析失败", it) }
            .getOrNull()
            ?: runCatching { parsePaiYiPai(payload, selfUin) }
                .onFailure { LogUtils.androidNoFilter.w("群事件: 拍一拍推送解析失败", it) }
                .getOrNull()
    }

    /**
     * 解析拍一拍推送。
     *
     * 群里内容是一段类似 XML 的文本（`uin_str1` / `uin_str2`），私聊则在数组里
     * 找 `uin_str2` 的取值。找不到或不是拍我，返回 null。
     */
    fun parsePaiYiPai(payload: ByteArray, selfUin: String): GroupEvent.PaiYiPai? {
        val root = decode(payload)

        val head = root.getOrNull(1, 2)?.asMapOrNull() ?: return null
        val tag1 = head.getOrNull(1)?.asLongOrNull() ?: return null
        val tag2 = head.getOrNull(2)?.asLongOrNull() ?: return null

        // 会话标识：群聊是群号，私聊是对方 QQ 号
        val peer = root.getOrNull(1, 1, 1)?.asNumberText().orEmpty()

        val chatType: Int
        val fromUin: String
        val toUin: String

        when {
            tag1 == PAI_GROUP_TAG_1.toLong() && tag2 == PAI_GROUP_TAG_2.toLong() -> {
                chatType = ScriptChatType.GROUP
                val content = paiContent(root)
                fromUin = extractUin(content, "1")
                toUin = extractUin(content, "2")
            }

            tag1 == PAI_FRIEND_TAG_1.toLong() && tag2 == PAI_FRIEND_TAG_2.toLong() -> {
                chatType = ScriptChatType.FRIEND
                fromUin = peer
                toUin = extractToUinFromList(root.getOrNull(1, 3, 2, 7))
            }

            else -> return null
        }

        if (!QQ_PATTERN.matches(fromUin)) return null
        if (selfUin.isNotEmpty() && toUin != selfUin) return null

        return GroupEvent.PaiYiPai(groupUin = peer, chatType = chatType, fromUin = fromUin)
    }

    /**
     * 取群里拍一拍的文本内容。
     *
     * 真实推送里 `1.3.2` 就是那段文本；但无描述符解码器遇到「恰好能解析成
     * protobuf 的文本」时会把它当嵌套消息解出来，此时值落到子消息的 tag 2。
     * 两种形态都兼容，避免解码器差异导致事件丢失。
     */
    private fun paiContent(root: ProtoMap): String {
        val node = root.getOrNull(1, 3, 2) ?: return ""

        runCatching { node.asString.toStringUtf8() }.getOrNull()
            ?.takeIf { it.contains("uin_str") }
            ?.let { return it }

        val nested = node.asMapOrNull() ?: return ""
        return nested.getOrNull(2)?.asText().orEmpty()
    }

    /** 从 `uin_strN=123456` 形态的文本里抠出 QQ 号。 */
    private fun extractUin(target: String, which: String): String {
        val key = "uin_str$which"
        val keyIndex = target.indexOf(key)
        if (keyIndex < 0) return ""

        val start = keyIndex + key.length
        val digits = target.drop(start).indexOfFirst { it.isDigit() }
        if (digits < 0) return ""

        val realStart = start + digits

        // 结束位置：逗号（真实推送的分隔符）或引号（值被引号包裹时），取最近的
        val endComma = target.indexOf(',', realStart).takeIf { it >= 0 } ?: target.length
        val endQuote = target.indexOf('"', realStart).takeIf { it >= 0 } ?: target.length
        val realEnd = minOf(endComma, endQuote)

        return target.substring(realStart, realEnd)
    }

    /** 私聊形态：在数组里找 `1 = "uin_str2"` 那一项，取它的 `2`。 */
    private fun extractToUinFromList(node: com.owo233.tcqt.core.proto.ProtoValue?): String {
        val list = runCatching { node?.asList }.getOrNull() ?: return ""

        repeat(list.size()) { index ->
            val item = runCatching { list[index] }.getOrNull()?.asMapOrNull() ?: return@repeat
            val key = item.getOrNull(1)?.asText().orEmpty()
            if (key == "uin_str2") {
                return item.getOrNull(2)?.asText().orEmpty()
            }
        }
        return ""
    }

    /** 入群推送（来自 `TroopMemberAddPushProcessor` 的字节参数）。 */
    fun parseMemberAdd(payload: ByteArray): GroupEvent.MemberJoin? =
        runCatching {
            val root = decode(payload)

            // 3.2.1 = 群号，3.2.3 = 入群者 UID
            val groupUin = root.getOrNull(3, 2, 1)?.asNumberText() ?: return null
            val memberUid = root.getOrNull(3, 2, 3)?.asText().orEmpty()
            if (groupUin.isEmpty()) return null

            GroupEvent.MemberJoin(groupUin = groupUin, memberUin = "", memberUid = memberUid)
        }.onFailure { LogUtils.androidNoFilter.w("群事件: 入群推送解析失败", it) }
            .getOrNull()

    /**
     * 解析群禁言推送。
     *
     * 只认消息头 (732, 12) 这一种，避免把别的推送误判成群禁言。
     */
    fun parseShutUp(payload: ByteArray): GroupEvent.ShutUp? {
        val root = decode(payload)

        val head = root.getOrNull(1, 2)?.asMapOrNull() ?: return null
        val tag1 = head.getOrNull(1)?.asLongOrNull() ?: return null
        val tag2 = head.getOrNull(2)?.asLongOrNull() ?: return null
        if (tag1 != SHUT_UP_HEAD_TAG_1.toLong() || tag2 != SHUT_UP_HEAD_TAG_2.toLong()) return null

        val groupInfo = root.getOrNull(1, 3, 2)?.asMapOrNull() ?: return null

        val groupUin = groupInfo.getOrNull(1)?.asNumberText() ?: return null
        val operatorUid = groupInfo.getOrNull(4)?.asText().orEmpty()

        val shutUpInfo = groupInfo.getOrNull(5, 3)?.asMapOrNull() ?: return null
        val memberUid = shutUpInfo.getOrNull(1)?.asText().orEmpty()
        val duration = shutUpInfo.getOrNull(2)?.asLongOrNull() ?: 0L

        return GroupEvent.ShutUp(
            groupUin = groupUin,
            memberUin = "",
            memberUid = memberUid,
            durationSeconds = duration,
            operatorUin = operatorUid,
        )
    }

    private fun decode(payload: ByteArray): ProtoMap =
        ProtoUtils.decodeFromByteArray(payload, ProtoDecodeMode.COMPATIBLE)

    // ── ProtoValue 读取辅助（容错：类型对不上就当没有）─────────────────────

    private fun com.owo233.tcqt.core.proto.ProtoValue.asMapOrNull(): ProtoMap? =
        runCatching { asMap }.getOrNull()

    private fun com.owo233.tcqt.core.proto.ProtoValue.asLongOrNull(): Long? =
        runCatching { asLong }.getOrNull()

    /** 数值字段（如群号）取十进制文本。 */
    private fun com.owo233.tcqt.core.proto.ProtoValue.asNumberText(): String? =
        runCatching { asLong.toString() }.getOrNull()

    /** 字符串字段取 UTF-8 文本。 */
    private fun com.owo233.tcqt.core.proto.ProtoValue.asText(): String =
        runCatching { asString.toStringUtf8() }.getOrDefault("")
}
