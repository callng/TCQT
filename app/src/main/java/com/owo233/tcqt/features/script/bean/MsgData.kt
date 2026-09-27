package com.owo233.tcqt.features.script.bean

import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

/**
 * 脚本可见的消息对象，由宿主 [MsgRecord] 投影而来。
 *
 * [data] / [contact] 保留宿主原始对象，脚本可自行反射取更多字段。
 * 文本走 [msg]，图片以 `[pic=url]` 内联，文件 / 语音 / 视频地址走 [path]。
 */
class MsgData(@JvmField val data: MsgRecord) {

    /** 聊天类型：1 好友 / 2 群聊 / 100 陌生人。 */
    @JvmField
    val type: Int = data.chatType

    @JvmField
    val msgType: Int = data.msgType

    /** 群号或好友 QQ 号。 */
    @JvmField
    val peerUin: String = data.peerUin.toString()

    @JvmField
    val peerUid: String = data.peerUid ?: ""

    /** 发送者 QQ 号。 */
    @JvmField
    val userUin: String = data.senderUin.toString()

    @JvmField
    val userUid: String = data.senderUid ?: ""

    /** 发送时间戳（秒）。 */
    @JvmField
    val time: Long = data.msgTime

    @JvmField
    val msgId: Long = data.msgId

    @JvmField
    val contact: Contact = Contact(data.chatType, data.peerUid, data.guildId ?: "")

    /** 文本内容，图片以 `[pic=url]` 形式内联。 */
    @JvmField
    var msg: String = ""

    /** 被艾特的 QQ 号列表。 */
    @JvmField
    val atList: ArrayList<String> = ArrayList()

    /** 艾特映射：QQ 号 -> 艾特展示文本。 */
    @JvmField
    val atMap: HashMap<String, String> = HashMap()

    /** 文件 / 语音 / 视频的本地路径（可能为空）。 */
    @JvmField
    var path: String = ""

    init {
        processElements(data.elements)
    }

    private fun processElements(elements: ArrayList<MsgElement>?) {
        val builder = StringBuilder()
        elements?.forEach { element ->
            when (element.elementType) {
                // 文本
                ELEMENT_TEXT -> {
                    val text = element.textElement ?: return@forEach
                    builder.append(text.content)
                    collectAt(text)
                }
                // 图片
                ELEMENT_PIC -> element.picElement?.originImageUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { builder.append("[pic=$it]") }
                // 文件
                ELEMENT_FILE -> element.fileElement?.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path += it }
                // 视频
                ELEMENT_VIDEO -> element.videoElement?.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path += it }
                // 语音
                ELEMENT_PTT -> element.pttElement?.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path += it }
                // ark 卡片
                ELEMENT_ARK -> element.arkElement?.bytesData
                    ?.takeIf { it.isNotBlank() }
                    ?.let { builder.append(it) }
            }
        }
        msg = builder.toString()
    }

    /** 艾特信息按 UID 承载，UIN 由宿主转换；转换失败时退化为原始 UID。 */
    private fun collectAt(text: com.tencent.qqnt.kernel.nativeinterface.TextElement) {
        val raw = text.atNtUid?.takeIf { it.isNotBlank() } ?: return
        val uin = runCatching { ScriptAtUinConverter.toUin(raw) }.getOrDefault(raw)
        if (uin.isBlank()) return
        atList.add(uin)
        atMap[uin] = text.content ?: ""
    }

    private companion object {
        const val ELEMENT_TEXT = 1
        const val ELEMENT_PIC = 2
        const val ELEMENT_FILE = 3
        const val ELEMENT_PTT = 4
        const val ELEMENT_VIDEO = 5
        const val ELEMENT_ARK = 10
    }
}

/**
 * UIN / UID 转换回调：由脚本 API 层注入宿主实现，避免 bean 直接依赖宿主服务。
 */
object ScriptAtUinConverter {

    @Volatile
    var converter: ((String) -> String)? = null

    fun toUin(uid: String): String = converter?.invoke(uid).orEmpty()
}
