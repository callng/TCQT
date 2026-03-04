package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.utils.MethodHookParam
import kotlinx.coroutines.DelicateCoroutinesApi
import top.artmoe.inao.entries.InfoSyncPushOuterClass
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

@OptIn(DelicateCoroutinesApi::class)
object AioListenerDispatcher {

    private val operationService = MessageOperationService()
    private val tipService = RecallTipService()

    private val handlers: List<MessageHandler> = listOf(
        C2CRecallHandler(operationService, tipService),
        GroupRecallHandler(operationService, tipService),
        FlashPicHandler(operationService, tipService)
    )

    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = MsgPushOuterClass.MsgPush.parseFrom(buffer)
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq

        handlers.find { it.canHandle(msgType, subType) }
            ?.handleMsgPush(msgPush, param)
    }

    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = InfoSyncPushOuterClass.InfoSyncPush.parseFrom(buffer)

        if (infoSyncPush.pushFlag != MessageType.INFO_SYNC_PUSH_FLAG_RECALL) return
        if (!infoSyncPush.hasSyncMsgRecall()) return

        val newBuilder = infoSyncPush.toBuilder()
        val syncRecall = infoSyncPush.syncMsgRecall
        val syncRecallBuilder = syncRecall.toBuilder()
        var hasChanges = false

        for (i in 0 until syncRecall.syncInfoBodyCount) {
            val body = syncRecall.getSyncInfoBody(i)
            val bodyBuilder = body.toBuilder()
            val originalMessages = body.msgList
            val keptMessages = mutableListOf<QQMessageOuterClass.QQMessage>()

            originalMessages.forEach { msg ->
                val shouldFilter = handlers.any { it.shouldFilterFromInfoSync(msg) }

                if (shouldFilter) {
                    handlers.forEach { it.onMessageFiltered(msg) }
                    hasChanges = true
                } else {
                    keptMessages.add(msg)
                }
            }

            if (keptMessages.size != originalMessages.size) {
                bodyBuilder.clearMsg().addAllMsg(keptMessages)
                syncRecallBuilder.setSyncInfoBody(i, bodyBuilder.build())
            }
        }

        if (hasChanges) {
            newBuilder.setSyncMsgRecall(syncRecallBuilder.build())
            param.args[1] = newBuilder.build().toByteArray()
        }
    }
}
