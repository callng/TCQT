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
     * 真实结构（真机抓包核对，QQ 9.3.70）：
     * ```
     * 1.1.1   = 群号（私聊时是对方 QQ）
     * 1.1.5   = 本机（被拍者）QQ
     * 1.2.1/2 = 732/20（群）或 528/290（私聊）
     * 1.3.2.7 = 键值对数组：[{1:"uin_str1",2:"发起者QQ"}, {1:"uin_str2",2:"被拍者QQ"},
     *                        {1:"action_str",2:"戳了戳"}, …]
     * 1.3.2.8 = [ 二进制, "<gtip …>XML…" ]
     * ```
     *
     * **不解析 XML / 不做正则**：`1.3.2.7` 里已经是结构化的键值对，直接取最稳。
     * （早期版本曾按 `uin_str1="X"` 抠字符串，真实数据里该字符串根本不在 `1.3.2` 上。）
     */
    fun parsePaiYiPai(payload: ByteArray, selfUin: String): GroupEvent.PaiYiPai? {
        val root = decode(payload)

        val head = root.getOrNull(1, 2)?.asMapOrNull() ?: return null
        val tag1 = head.getOrNull(1)?.asLongOrNull() ?: return null
        val tag2 = head.getOrNull(2)?.asLongOrNull() ?: return null

        val chatType = when {
            tag1 == PAI_GROUP_TAG_1.toLong() && tag2 == PAI_GROUP_TAG_2.toLong() ->
                ScriptChatType.GROUP

            tag1 == PAI_FRIEND_TAG_1.toLong() && tag2 == PAI_FRIEND_TAG_2.toLong() ->
                ScriptChatType.FRIEND

            else -> return null
        }

        // 会话标识：群聊是群号，私聊是对方 QQ
        val peer = root.getOrNull(1, 1, 1)?.asNumberText().orEmpty()

        // 键值对数组：uin_str1 = 发起者，uin_str2 = 被拍者
        val fields = parseKeyValuePairs(root.getOrNull(1, 3, 2, 7))
        val fromUin = fields["uin_str1"].orEmpty()
        val toUin = fields["uin_str2"].orEmpty()
            // 兜底：1.1.5 是本机（被拍者）
            .ifEmpty { root.getOrNull(1, 1, 5)?.asNumberText().orEmpty() }

        if (!QQ_PATTERN.matches(fromUin)) return null
        if (selfUin.isNotEmpty() && toUin.isNotEmpty() && toUin != selfUin) return null

        return GroupEvent.PaiYiPai(groupUin = peer, chatType = chatType, fromUin = fromUin)
    }

    /**
     * 解析 `[{1:"key",2:"value"}, …]` 形态的键值对数组。
     *
     * 值可能是字符串，也可能是被解成嵌套消息的字节串，两种都兼容。
     */
    fun parseKeyValuePairs(node: com.owo233.tcqt.core.proto.ProtoValue?): Map<String, String> {
        val list = runCatching { node?.asList }.getOrNull() ?: return emptyMap()

        val result = linkedMapOf<String, String>()
        repeat(list.size()) { index ->
            val item = runCatching { list[index] }.getOrNull()?.asMapOrNull() ?: return@repeat
            val key = item.getOrNull(1)?.asText().orEmpty()
            if (key.isEmpty()) return@repeat
            result[key] = item.getOrNull(2)?.asText().orEmpty()
        }
        return result
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

    /** 诊断用：解码整个推送体。 */
    fun decodeForDiag(payload: ByteArray): ProtoMap = decode(payload)

    /**
     * 解码推送体。
     *
     * 部分推送（真机抓包确认）前面带 **4 字节大端长度前缀**，其值等于「总长」；
     * 直接当 protobuf 解会因为 tag=0 非法而失败，所以这里识别并剥掉。
     */
    private fun decode(payload: ByteArray): ProtoMap {
        val body = stripLengthPrefix(payload)
        return ProtoUtils.decodeFromByteArray(body, ProtoDecodeMode.COMPATIBLE)
    }

    /** 若前 4 字节是大端长度且正好等于总长，则剥掉；否则原样返回。 */
    private fun stripLengthPrefix(payload: ByteArray): ByteArray {
        if (payload.size <= 4) return payload

        val declared = ((payload[0].toInt() and 0xff) shl 24) or
                ((payload[1].toInt() and 0xff) shl 16) or
                ((payload[2].toInt() and 0xff) shl 8) or
                (payload[3].toInt() and 0xff)

        return if (declared == payload.size) payload.copyOfRange(4, payload.size) else payload
    }

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
