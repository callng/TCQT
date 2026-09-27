package com.owo233.tcqt.features.script

import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.msg.api.IMsgUtilApi
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces
import java.io.File

/**
 * 脚本消息元素工厂：包装宿主的 `IMsgUtilApi`。
 *
 * 原先直接 `MsgElement().apply { textElement = ... }` 拼元素，只支持纯文本；
 * 宿主这套工厂能正确处理 at / 图片 / 语音 / 视频 / 文件 / 引用所需的内部字段。
 */
internal object ScriptElementFactory {

    private val api: IMsgUtilApi? by lazy {
        runCatching { QQInterfaces.api<IMsgUtilApi>() }
            .onFailure { LogUtils.androidNoFilter.w("脚本引擎: 取 IMsgUtilApi 失败", it) }
            .getOrNull()
    }

    /** 宿主工厂不可用时（版本差异）退化成手拼纯文本元素。 */
    private fun fallbackText(content: String): MsgElement = MsgElement().apply {
        textElement = com.tencent.qqnt.kernel.nativeinterface.TextElement().apply {
            this.content = content
        }
    }

    fun text(content: String): MsgElement =
        api?.runCatching { createTextElement(content) }?.getOrNull()
            ?: fallbackText(content)

    /**
     * 艾特元素。
     *
     * @param atUin 被艾特 QQ 号；`0` 表示全体成员
     */
    fun at(atUin: String, chatType: Int): MsgElement? {
        if (chatType != ScriptRichText.CHAT_GROUP) return null

        val service = api ?: return fallbackText(atDisplayText(atUin))
        return runCatching {
            if (atUin == "0") {
                service.createAtTextElement("@全体成员", "0", AT_TYPE_ALL)
            } else {
                val uid = ScriptContact.uidOf(chatType, atUin)
                service.createAtTextElement(atDisplayText(atUin), uid, AT_TYPE_SINGLE)
            }
        }.getOrNull()
    }

    fun pic(path: String): MsgElement? =
        api?.runCatching { createPicElement(path, true, 0) }?.getOrNull()

    fun video(path: String): MsgElement? =
        api?.runCatching { createVideoElement(path) }?.getOrNull()

    fun file(path: String): MsgElement? =
        api?.runCatching { createFileElement(path) }?.getOrNull()

    /** 语音：[durationMs] 为 0 时按 SILK 头估算，估不出退 1000ms。 */
    fun ptt(path: String, durationMs: Int): MsgElement? {
        val service = api ?: return null
        val millis = (if (durationMs > 0) durationMs else SilkDuration.estimate(path))
            .coerceAtLeast(MIN_PTT_MS)

        return runCatching {
            service.createPttElement(path, millis, PttWaveform.build(millis))
        }.getOrNull()
            ?: runCatching { service.createPttElement(path, millis) }.getOrNull()
    }

    fun reply(replyMsgId: Long): MsgElement? =
        api?.runCatching { createReplyElement(replyMsgId) }?.getOrNull()

    /** 艾特的展示文本；单人事先拿不到昵称，用 `@QQ号` 占位（宿主会按 uid 渲染）。 */
    private fun atDisplayText(atUin: String): String = "@$atUin"

    /** `TextElement.atType`：1 全体 / 2 单人。 */
    const val AT_TYPE_ALL = 1
    const val AT_TYPE_SINGLE = 2

    private const val MIN_PTT_MS = 1000

    /** 图片下载缓存目录。 */
    val imageCacheDir: File
        get() = File(HookEnv.moduleDataPath, "script/.cache/images").apply { mkdirs() }
}

/**
 * 语音时长估算：解析 SILK 帧头累加帧数（每帧 20ms）。
 *
 * 宿主 `createPttElement` 需要毫秒时长，传 0 会让部分版本的语音条不显示。
 */
internal object SilkDuration {

    private const val SILK_HEADER = "#!SILK_V3"
    private const val FRAME_MS = 20
    private const val MAX_FRAME_BYTES = 4096

    fun estimate(path: String): Int = runCatching {
        val bytes = File(path).takeIf { it.exists() && it.length() > 0L }?.readBytes()
            ?: return FALLBACK_MS

        var offset = when {
            bytes.size >= 10 && String(bytes, 1, 9, Charsets.US_ASCII) == SILK_HEADER -> 10
            bytes.size >= 9 && String(bytes, 0, 9, Charsets.US_ASCII) == SILK_HEADER -> 9
            else -> return FALLBACK_MS
        }

        var frames = 0
        while (offset + 2 <= bytes.size) {
            val frameLen = (bytes[offset].toInt() and 0xff) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 8)
            offset += 2
            if (frameLen <= 0 || frameLen > MAX_FRAME_BYTES || offset + frameLen > bytes.size) break
            offset += frameLen
            frames++
        }

        if (frames <= 0) FALLBACK_MS else frames * FRAME_MS
    }.getOrDefault(FALLBACK_MS)

    const val FALLBACK_MS = 1000
}

/**
 * 语音波形：为空时部分版本语音条不显示，这里生成一段确定性波形。
 */
internal object PttWaveform {

    fun build(durationMs: Int): ArrayList<Byte> {
        val count = (durationMs / 40).coerceIn(20, 120)
        return ArrayList<Byte>(count).apply {
            for (i in 0 until count) add((15 + (i * 7) % 70).toByte())
        }
    }
}
