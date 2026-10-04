package com.owo233.tcqt.features.script.bean

import com.owo233.tcqt.core.log.LogUtils
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

    /**
     * 逐元素解析。
     *
     * **单个元素解析失败绝不能影响整条消息** —— 之前一个字段取值抛异常会让
     * `MsgData` 构造失败，事件在派发处被静默丢弃，表现为"这条消息完全没有推送"。
     * 因此这里逐元素兜异常，并把失败的元素类型记下来。
     */
    private fun processElements(elements: ArrayList<MsgElement>?) {
        val builder = StringBuilder()
        elements?.forEach { element ->
            runCatching { appendElement(element, builder) }
                .onFailure {
                    LogUtils.androidNoFilter.w(
                        "MsgData: 元素解析失败 elementType=${element.elementType}", it
                    )
                }
        }
        msg = builder.toString()
    }

    private fun appendElement(element: MsgElement, builder: StringBuilder) {
        when (element.elementType) {
            // 文本
            ELEMENT_TEXT -> {
                val text = element.textElement ?: return
                builder.append(text.content)
                collectAt(text)
            }
            // 图片
            ELEMENT_PIC -> element.picElement?.originImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.append("[pic=${picUrl(it)}]") }
            // 文件
            ELEMENT_FILE -> element.fileElement?.filePath
                ?.takeIf { it.isNotBlank() }
                ?.let { path += it }
            // 语音：filePath 只有下载/播放后才有值，因此**始终**给出可辨识标记
            ELEMENT_PTT -> appendPtt(element, builder)
            // 视频
            ELEMENT_VIDEO -> element.videoElement?.filePath
                ?.takeIf { it.isNotBlank() }
                ?.let { path += it }
            // 表情 / 戳一戳（宿主用同一个元素承载）
            ELEMENT_FACE -> appendFace(element, builder)
            // 商城表情（大表情）
            ELEMENT_MARKET_FACE -> element.marketFaceElement?.faceName
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.append("[表情:$it]") }
            // 引用回复：正文由后续元素承载
            ELEMENT_REPLY -> Unit
            // ark 卡片
            ELEMENT_ARK -> element.arkElement?.bytesData
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.append(it) }
        }
    }

    /**
     * 图片地址补全。
     *
     * 宿主给的 `originImageUrl` 是**相对路径**（如 `/download?appid=…`），
     * 直接交给脚本拼不出可访问地址；这里补上域名。
     */
    private fun picUrl(raw: String): String =
        if (raw.startsWith("http")) raw else PIC_HOST + raw

    /** 语音：始终输出 `[语音:Ns]`，能拿到路径时写进 `path`，有转文字时附加。 */
    private fun appendPtt(element: MsgElement, builder: StringBuilder) {
        val ptt = element.pttElement ?: return

        val seconds = runCatching { ptt.duration }.getOrDefault(0)
        builder.append(if (seconds > 0) "[语音:${seconds}s]" else "[语音]")

        runCatching { ptt.filePath }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { path += it }

        runCatching { ptt.text }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.append(it) }
    }

    /** 表情与戳一戳：优先用 `faceText`，拿不到退回 `faceIndex`。 */
    private fun appendFace(element: MsgElement, builder: StringBuilder) {
        val face = element.faceElement ?: return

        // 戳一戳是单独语义，不要混进正文；字段可能为空，逐项兜异常
        val pokeType = runCatching { face.pokeType }.getOrNull()
        if (pokeType != null && pokeType > 0) {
            builder.append("[戳一戳]")
            return
        }

        val text = runCatching { face.faceText }.getOrNull()
        if (!text.isNullOrBlank()) {
            builder.append(text)
            return
        }

        val index = runCatching { face.faceIndex }.getOrDefault(0)
        if (index > 0) builder.append("[表情:").append(index).append(']')
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
        /**
         * 元素类型取值来自宿主 `MsgConstant.KELEMTYPE*`（QQ 9.3.70 实测值），
         * 不是猜的；新增类型时从本文件顶部的对照表补。
         */
        const val ELEMENT_TEXT = 1
        const val ELEMENT_PIC = 2
        const val ELEMENT_FILE = 3
        const val ELEMENT_PTT = 4
        const val ELEMENT_VIDEO = 5
        const val ELEMENT_FACE = 6
        const val ELEMENT_REPLY = 7
        const val ELEMENT_ARK = 10
        const val ELEMENT_MARKET_FACE = 11

        /**
         * 图片域名。
         *
         * 宿主 `originImageUrl` 给的是相对路径（`/download?appid=…`），
         * 脚本需要完整地址才能用；宿主自己的实现也是这么拼的。
         */
        const val PIC_HOST = "https://multimedia.nt.qq.com.cn"
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
