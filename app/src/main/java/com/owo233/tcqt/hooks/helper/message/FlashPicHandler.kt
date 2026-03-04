package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.MethodHookParam
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.artmoe.inao.entries.MsgPushOuterClass

@OptIn(DelicateCoroutinesApi::class)
class FlashPicHandler(
    private val operationService: MessageOperationService,
    private val tipService: RecallTipService
) : MessageHandler {

    override fun canHandle(msgType: Int, subType: Int): Boolean {
        return msgType == MessageType.MSG_TYPE_FLASH_PIC &&
                subType == MessageType.SUB_TYPE_FLASH_PIC &&
                GeneratedSettingList.getBoolean(GeneratedSettingList.DISABLE_FLASH_PIC)
    }

    override fun handleMsgPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        if (operationService.isFlashPic(msgPush)) {
            val msg = msgPush.qqMessage
            val operatorUid = msg.messageHead.senderUid
            if (operatorUid == QQInterfaces.currentUid) return

            val msgSeq = msg.messageContentInfo.msgSeqId

            GlobalScope.launch {
                delay(300L)
                tipService.showFlashPicTip(operatorUid, msgSeq.toLong())
            }
        }
    }
}
