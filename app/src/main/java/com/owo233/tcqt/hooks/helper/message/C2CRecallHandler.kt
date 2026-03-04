package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.MethodHookParam
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

class C2CRecallHandler(
    private val operationService: MessageOperationService,
    private val tipService: RecallTipService
) : MessageHandler {

    override fun canHandle(msgType: Int, subType: Int): Boolean {
        return msgType == MessageType.MSG_TYPE_C2C && subType == MessageType.SUB_TYPE_C2C_RECALL
    }

    override fun handleMsgPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val opInfoBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()
        val operationInfo = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opInfoBytes)

        val operatorUid = operationInfo.info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return

        val newOpBytes = operationService.modifyC2CRecallOperation(operationInfo)
        val newMsgPush = operationService.updateMsgPushOperation(msgPush, newOpBytes)
        param.args[1] = newMsgPush.toByteArray()

        tipService.showC2CRecallTip(operatorUid, operationInfo.info.msgSeq)
    }

    override fun shouldFilterFromInfoSync(msg: QQMessageOuterClass.QQMessage): Boolean {
        val type = msg.messageContentInfo.msgType
        val sub = msg.messageContentInfo.subSeq
        return canHandle(type, sub)
    }

    override fun onMessageFiltered(msg: QQMessageOuterClass.QQMessage) {
        operationService.extractC2CRecallInfo(msg)?.let { (senderUid, msgSeq) ->
            tipService.showC2CRecallTip(senderUid, msgSeq.toInt())
        }
    }
}
