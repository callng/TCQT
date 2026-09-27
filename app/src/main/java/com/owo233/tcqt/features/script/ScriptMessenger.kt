package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.ContactHelper
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import com.tencent.qqnt.msg.api.IMsgService
import kotlinx.coroutines.runBlocking

/**
 * 脚本消息收发：统一走 NT 内核 `IMsgService`，与模块内其它功能同一条通路。
 *
 * 元素构造交给 [ScriptElementFactory]（宿主 `IMsgUtilApi`），因此：
 * - 文本支持 `[atUin=]` / `[pic=]` 富文本（见 [ScriptRichText]）；
 * - at / 图片 / 语音 / 视频 / 文件 / 引用所需的内部字段由宿主自己填，不需要手拼。
 *
 * 发送不阻塞调用线程，脚本不需要关心线程模型。
 */
internal object ScriptMessenger {

    // ── 文本（含富文本） ─────────────────────────────────────────────────

    /** 按 QQ 号发送；文本里的 `[atUin=]` / `[pic=]` 会被解析成对应元素。 */
    fun sendText(chatType: Int, peerUin: String, msg: String) {
        sendText(ScriptContact.contactOf(chatType, peerUin), msg)
    }

    fun sendText(contact: Contact, msg: String) {
        val elements = buildRichElements(contact, msg)
        if (elements.isEmpty()) return
        send(contact, elements, "文本")
    }

    /**
     * 把脚本文本转成元素列表。
     *
     * `[atUin=]` 只在群聊生效（与文档一致）；`[pic=]` 下载失败时降级成提示文本，
     * 而不是静默丢弃整条消息。
     */
    fun buildRichElements(contact: Contact, msg: String): ArrayList<MsgElement> {
        val parts = ScriptRichText.parse(msg)
        val elements = ArrayList<MsgElement>(parts.size)

        parts.forEach { part ->
            when (part.kind) {
                ScriptRichText.PartKind.TEXT ->
                    ScriptElementFactory.text(part.value).let(elements::add)

                ScriptRichText.PartKind.AT -> {
                    val atUin = part.value
                    ScriptElementFactory.at(atUin, contact.chatType)?.let(elements::add)
                        ?: elements.add(ScriptElementFactory.text("@$atUin"))
                }

                ScriptRichText.PartKind.PIC -> {
                    val path = ScriptRichText.resolvePicPath(part.value)
                    val element = path?.let(ScriptElementFactory::pic)
                    elements.add(element ?: ScriptElementFactory.text("[图片下载失败]"))
                }
            }
        }

        return elements
    }

    // ── 单一类型消息 ─────────────────────────────────────────────────────

    fun sendPic(chatType: Int, peerUin: String, path: String) {
        sendPath(chatType, peerUin, path, "图片") { ScriptElementFactory.pic(it) }
    }

    fun sendPic(contact: Contact, path: String) {
        sendPath(contact, path, "图片") { ScriptElementFactory.pic(it) }
    }

    fun sendPtt(chatType: Int, peerUin: String, path: String, durationMs: Int) {
        sendPath(chatType, peerUin, path, "语音") { ScriptElementFactory.ptt(it, durationMs) }
    }

    fun sendPtt(contact: Contact, path: String, durationMs: Int) {
        sendPath(contact, path, "语音") { ScriptElementFactory.ptt(it, durationMs) }
    }

    fun sendFile(chatType: Int, peerUin: String, path: String) {
        sendPath(chatType, peerUin, path, "文件") { ScriptElementFactory.file(it) }
    }

    fun sendFile(contact: Contact, path: String) {
        sendPath(contact, path, "文件") { ScriptElementFactory.file(it) }
    }

    fun sendVideo(chatType: Int, peerUin: String, path: String) {
        sendPath(chatType, peerUin, path, "视频") { ScriptElementFactory.video(it) }
    }

    fun sendVideo(contact: Contact, path: String) {
        sendPath(contact, path, "视频") { ScriptElementFactory.video(it) }
    }

    fun sendCard(chatType: Int, peerUin: String, data: String) {
        sendPath(chatType, peerUin, data, "卡片", ::ark)
    }

    fun sendCard(contact: Contact, data: String) {
        sendPath(contact, data, "卡片", ::ark)
    }

    // ── 引用回复 ─────────────────────────────────────────────────────────

    fun sendReply(chatType: Int, peerUin: String, replyMsgId: Long, msg: String) {
        sendReply(ScriptContact.contactOf(chatType, peerUin), replyMsgId, msg)
    }

    fun sendReply(contact: Contact, replyMsgId: Long, msg: String) {
        val elements = ArrayList<MsgElement>()
        ScriptElementFactory.reply(replyMsgId)?.let(elements::add)
        elements.addAll(buildRichElements(contact, msg))
        if (elements.isEmpty()) return
        send(contact, elements, "回复")
    }

    // ── 撤回 ─────────────────────────────────────────────────────────────

    fun recall(chatType: Int, peerUin: String, msgId: Long) {
        recall(ScriptContact.contactOf(chatType, peerUin), msgId)
    }

    fun recall(contact: Contact, msgId: Long) {
        runCatching {
            QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgId)) { code, err ->
                if (code != 0) LogUtils.androidNoFilter.e("脚本撤回消息失败: $err ($code)")
            }
        }.onFailure { LogUtils.androidNoFilter.e("脚本撤回消息异常", it) }
    }

    fun sendPai(chatType: Int, peerUin: String, toUin: String) {
        ScriptPai.send(chatType, peerUin, toUin)
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private inline fun sendPath(
        chatType: Int,
        peerUin: String,
        path: String,
        label: String,
        build: (String) -> MsgElement?,
    ) = sendPath(ScriptContact.contactOf(chatType, peerUin), path, label, build)

    /** 用 [path] 构造单个元素并发送；构造失败记日志并放弃（不发半个元素出去）。 */
    private inline fun sendPath(
        contact: Contact,
        path: String,
        label: String,
        build: (String) -> MsgElement?,
    ) {
        val element = build(path)
        if (element == null) {
            LogUtils.androidNoFilter.w("脚本发送$label 失败：无法构造元素（$path）")
            return
        }
        send(contact, arrayListOf(element), label)
    }

    private fun send(contact: Contact, elements: ArrayList<MsgElement>, label: String) {
        Thread {
            runCatching {
                // 与 RepeatMessage 同一条通路：QRoute 的 IMsgService 是公开 Contact 签名。
                QQInterfaces.api<IMsgService>()
                    .sendMsg(contact, elements) { code, err ->
                        if (code != 0) {
                            LogUtils.androidNoFilter.e("脚本发送$label 失败: $err ($code)")
                        }
                    }
            }.onFailure { LogUtils.androidNoFilter.e("脚本发送$label 异常", it) }
        }.apply { name = "TCQT-ScriptSend" }.start()
    }

    /** ark 卡片：宿主没有"字符串转 ark 元素"的工厂方法，退回手拼。 */
    private fun ark(data: String): MsgElement = MsgElement().apply {
        arkElement = com.tencent.qqnt.kernel.nativeinterface.ArkElement().apply {
            bytesData = data
        }
    }
}