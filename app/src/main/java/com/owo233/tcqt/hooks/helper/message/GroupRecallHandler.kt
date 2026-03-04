package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.MethodHookParam
import kotlinx.coroutines.runBlocking
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

class GroupRecallHandler(
    private val operationService: MessageOperationService,
    private val tipService: RecallTipService
) : MessageHandler {

    override fun canHandle(msgType: Int, subType: Int): Boolean {
        return msgType == MessageType.MSG_TYPE_GROUP && subType == MessageType.SUB_TYPE_GROUP_RECALL
    }

    override fun handleMsgPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val fullOpBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()

        val splitResult = operationService.splitGroupOperationBytes(fullOpBytes) ?: return
        val (headerBytes, bodyBytes) = splitResult

        val operationInfo = QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo.parseFrom(bodyBytes)

        val operatorUid = operationInfo.info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return

        val modifiedBodyBytes = operationService.modifyGroupRecallOperation(operationInfo)
        val newFullOpBytes = headerBytes + modifiedBodyBytes
        val newMsgPush = operationService.updateMsgPushOperation(msgPush, newFullOpBytes)
        param.args[1] = newMsgPush.toByteArray()

        runBlocking {
            tipService.showGroupRecallTip(operationInfo)
        }
    }

    override fun shouldFilterFromInfoSync(msg: QQMessageOuterClass.QQMessage): Boolean {
        val type = msg.messageContentInfo.msgType
        val sub = msg.messageContentInfo.subSeq
        return canHandle(type, sub)
    }
}
