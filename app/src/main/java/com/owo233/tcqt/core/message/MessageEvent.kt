package com.owo233.tcqt.core.message

import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

/** 消息事件来自哪条通路。 */
enum class MessageKind {

    /** 服务端推送落地的消息（`msgService.onRecvMsg`），包含别人发来的消息。 */
    RECEIVE,

    /** 本机发出的消息（`msgService.onAddSendMsg`）。 */
    SEND,
}

/**
 * 一条消息的不可变快照：视图层与脚本层的公共输入。
 *
 * 保留原始 [record]，需要额外字段时可直接读宿主对象；只读展示用下面的取值器，
 * 它们对 null 安全（宿主字段可能为空）。
 */
class MessageEvent(
    @JvmField val kind: MessageKind,
    @JvmField val record: MsgRecord,
) {

    @JvmField
    val chatType: Int = record.chatType

    @JvmField
    val msgId: Long = record.msgId

    @JvmField
    val msgTime: Long = record.msgTime

    @JvmField
    val peerUid: String = record.peerUid ?: ""

    @JvmField
    val peerUin: String = record.peerUin.toString()

    @JvmField
    val senderUid: String = record.senderUid ?: ""

    @JvmField
    val senderUin: String = record.senderUin.toString()

    @JvmField
    val msgType: Int = record.msgType

    override fun toString(): String =
        "MessageEvent(kind=$kind, chatType=$chatType, msgId=$msgId, peer=$peerUin, sender=$senderUin)"
}

/**
 * 消息监听器。
 *
 * 实现类由 [MessageDispatcher.register] 注册，运行在**收到推送的那个进程**里。
 */
fun interface MessageListener {

    fun onMessage(event: MessageEvent)
}
