package com.owo233.tcqt.hooks.helper.message

import com.google.protobuf.ByteString
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

class MessageOperationService {

    fun modifyC2CRecallOperation(operationInfo: QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo): ByteArray {
        return operationInfo.toBuilder()
            .apply {
                info = info.toBuilder().setMsgSeq(1).build()
            }
            .build()
            .toByteArray()
    }

    fun modifyGroupRecallOperation(operationInfo: QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo): ByteArray {
        return operationInfo.toBuilder()
            .apply {
                msgSeq = 1
                info = info.toBuilder().apply {
                    msgInfo = msgInfo.toBuilder().setMsgSeq(1).build()
                }.build()
            }
            .build()
            .toByteArray()
    }

    fun updateMsgPushOperation(msgPush: MsgPushOuterClass.MsgPush, newOpBytes: ByteArray): MsgPushOuterClass.MsgPush {
        return msgPush.toBuilder()
            .apply {
                qqMessage = qqMessage.toBuilder()
                    .apply {
                        messageBody = messageBody.toBuilder()
                            .apply {
                                setOperationInfo(ByteString.copyFrom(newOpBytes))
                            }
                            .build()
                    }
                    .build()
            }
            .build()
    }

    fun extractC2CRecallInfo(qqMessage: QQMessageOuterClass.QQMessage): Pair<String, Long>? {
        return try {
            val opInfo = qqMessage.messageBody.operationInfo
            val c2cRecall = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opInfo)
            val msgSeq = c2cRecall.info.msgSeq.toLong()
            val senderUid = qqMessage.messageHead.senderUid
            senderUid to msgSeq
        } catch (_: Exception) {
            null
        }
    }

    fun isFlashPic(msgPush: MsgPushOuterClass.MsgPush): Boolean {
        val contentList = msgPush.qqMessage.messageBody.richMsg.msgContentList
        return contentList.getOrNull(2)?.myCustomField?.mtType == 3
    }

    fun splitGroupOperationBytes(fullOpBytes: ByteArray): Pair<ByteArray, ByteArray>? {
        if (fullOpBytes.size <= MessageType.GROUP_OP_HEADER_SIZE) return null
        val header = fullOpBytes.copyOfRange(0, MessageType.GROUP_OP_HEADER_SIZE)
        val body = fullOpBytes.copyOfRange(MessageType.GROUP_OP_HEADER_SIZE, fullOpBytes.size)
        return header to body
    }
}
