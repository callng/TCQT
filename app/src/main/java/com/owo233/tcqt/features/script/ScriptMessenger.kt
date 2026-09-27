package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.ContactHelper
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.qqnt.kernel.nativeinterface.ArkElement
import com.tencent.qqnt.kernel.nativeinterface.FileElement
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.kernel.nativeinterface.ReplyElement
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import com.tencent.qqnt.kernel.nativeinterface.VideoElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import com.tencent.qqnt.msg.api.IMsgService
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 脚本消息收发：统一走 NT 内核 `IKernelMsgService` / `IMsgService`，与模块内其它功能同一条通路。
 *
 * 发送不阻塞调用线程，脚本不需要关心线程模型。
 */
internal object ScriptMessenger {

    fun sendText(chatType: Int, peerUin: String, msg: String) {
        sendElement(chatType, peerUin, "文本") { it.textElement = text(msg) }
    }

    fun sendText(contact: Contact, msg: String) {
        sendElement(contact, "文本") { it.textElement = text(msg) }
    }

    fun sendReply(chatType: Int, peerUin: String, replyMsgId: Long, msg: String) {
        sendElement(chatType, peerUin, "回复") { element ->
            element.textElement = text(msg)
            element.replyElement = ReplyElement().apply { replayMsgId = replyMsgId }
        }
    }

    fun sendReply(contact: Contact, replyMsgId: Long, msg: String) {
        sendElement(contact, "回复") { element ->
            element.textElement = text(msg)
            element.replyElement = ReplyElement().apply { replayMsgId = replyMsgId }
        }
    }

    fun recall(chatType: Int, peerUin: String, msgId: Long) {
        recall(ScriptContact.contactOf(chatType, peerUin), msgId)
    }

    fun recall(contact: Contact, msgId: Long) {
        runCatching {
            QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgId)) { code, err ->
                if (code != 0) Log.e("脚本撤回消息失败: $err ($code)")
            }
        }.onFailure { Log.e("脚本撤回消息异常", it) }
    }

    fun sendPai(chatType: Int, peerUin: String, toUin: String) {
        Log.w("脚本 API sendPai 暂未适配宿主接口（$peerUin -> $toUin, chatType=$chatType）")
    }

    fun sendElement(chatType: Int, peerUin: String, label: String, fill: (MsgElement) -> Unit) {
        sendElement(ScriptContact.contactOf(chatType, peerUin), label, fill)
    }

    fun sendElement(contact: Contact, label: String, fill: (MsgElement) -> Unit) {
        val element = MsgElement().apply(fill)
        Thread {
            runCatching {
                // 与 RepeatMessage 同一条通路：QRoute 的 IMsgService 是公开 Contact 签名。
                QQInterfaces.api<IMsgService>()
                    .sendMsg(contact, arrayListOf(element)) { code, err ->
                        if (code != 0) Log.e("脚本发送$label 失败: $err ($code)")
                    }
            }.onFailure { Log.e("脚本发送$label 异常", it) }
        }.apply { name = "TCQT-ScriptSend" }.start()
    }

    // ── 元素构造 ─────────────────────────────────────────────────────────

    fun text(content: String): TextElement = TextElement().apply { this.content = content }

    fun pic(path: String): PicElement = PicElement().apply {
        val file = File(path)
        if (file.exists()) {
            sourcePath = file.absolutePath
            fileSize = file.length()
        } else {
            fileName = file.name
            originImageUrl = path
        }
    }

    fun ptt(path: String, durationMs: Int): PttElement = PttElement().apply {
        val file = File(path)
        filePath = path
        fileName = file.name
        fileSize = if (file.exists()) file.length() else 0L
        duration = if (durationMs > 0) durationMs else 1000
    }

    fun file(path: String): FileElement = FileElement().apply {
        val f = File(path)
        filePath = path
        fileName = f.name
        fileSize = if (f.exists()) f.length() else 0L
    }

    fun video(path: String): VideoElement = VideoElement().apply {
        val f = File(path)
        filePath = path
        fileName = f.name
        fileSize = if (f.exists()) f.length() else 0L
    }

    fun ark(data: String): ArkElement = ArkElement().apply { bytesData = data }
}

/** 把脚本传入的 QQ 号 / UID 转成宿主内核 `Contact`。 */internal object ScriptContact {

    fun contactOf(chatType: Int, peerUin: String): Contact = runBlocking {
        runCatching {
            val maple = if (peerUin.startsWith("u_")) {
                ContactHelper.generateContactByUid(chatType, peerUin)
            } else {
                ContactHelper.generateContact(chatType, peerUin)
            }
            maple.publicContact()
        }.getOrElse {
            Log.e("脚本 Contact 构造失败: chatType=$chatType, peer=$peerUin", it)
            Contact(chatType, peerUin, "")
        }
    }

    private fun MapleContact.publicContact(): Contact = when (this) {
        is MapleContact.PublicContact -> inner
        is MapleContact.Contact -> Contact(inner.chatType, inner.peerUid, inner.guildId ?: "")
    }
}
