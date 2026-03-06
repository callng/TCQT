package com.owo233.tcqt.hooks.helper

import com.google.protobuf.ByteString
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.MethodHookParam
import top.artmoe.inao.entries.InfoSyncPushOuterClass
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

object AioListener {
    private const val MSG_TYPE_C2C = 528
    private const val SUB_TYPE_C2C_RECALL = 138
    private const val MSG_TYPE_GROUP = 732
    private const val SUB_TYPE_GROUP_RECALL = 17
    private const val MSG_TYPE_FLASH_PIC = 166
    private const val SUB_TYPE_FLASH_PIC = 11
    private const val CUSTOM_FIELD_MT_TYPE_FLASH = 3
    private const val GROUP_OP_HEADER_SIZE = 7
    private const val INFO_SYNC_PUSH_FLAG_RECALL = 2
    private const val INVALID_MSG_SEQ = 1

    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = MsgPushOuterClass.MsgPush.parseFrom(buffer)
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq

        when (msgType) {
            MSG_TYPE_C2C if subType == SUB_TYPE_C2C_RECALL -> {
                processC2CRecallPush(msgPush, param)
            }
            MSG_TYPE_GROUP if subType == SUB_TYPE_GROUP_RECALL -> {
                processGroupRecallPush(msgPush, param)
            }
            MSG_TYPE_FLASH_PIC if subType == SUB_TYPE_FLASH_PIC -> {
                if (GeneratedSettingList.getBoolean(GeneratedSettingList.DISABLE_FLASH_PIC)) {
                    processFlashPicPush(msg)
                }
            }
        }
    }

    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = InfoSyncPushOuterClass.InfoSyncPush.parseFrom(buffer)
        if (infoSyncPush.pushFlag != INFO_SYNC_PUSH_FLAG_RECALL) return

        val interceptedC2CRecalls = mutableListOf<Pair<String, Long>>()
        var hasModifications = false

        val newInfoSyncPush = infoSyncPush.toBuilder().apply {
            syncMsgRecall = syncMsgRecall.toBuilder().apply {
                for (i in 0 until syncInfoBodyCount) {
                    val body = getSyncInfoBody(i)
                    val keptMessages = ArrayList<QQMessageOuterClass.QQMessage>(body.msgCount)
                    var bodyModified = false

                    for (msg in body.msgList) {
                        val type = msg.messageContentInfo.msgType
                        val sub = msg.messageContentInfo.subSeq

                        if (type == MSG_TYPE_GROUP && sub == SUB_TYPE_GROUP_RECALL) {
                            bodyModified = true
                            continue
                        }
                        if (type == MSG_TYPE_C2C && sub == SUB_TYPE_C2C_RECALL) {
                            extractC2CRecallInfo(msg)?.let { interceptedC2CRecalls.add(it) }
                            bodyModified = true
                            continue
                        }
                        keptMessages.add(msg)
                    }

                    if (bodyModified) {
                        hasModifications = true
                        setSyncInfoBody(i, body.toBuilder().clearMsg().addAllMsg(keptMessages).build())
                    }
                }
            }.build()
        }.build()

        if (hasModifications) {
            param.args[1] = newInfoSyncPush.toByteArray()
        }

        AioVisualHelper.showInterceptedC2CTips(interceptedC2CRecalls)
    }

    private fun processFlashPicPush(msg: QQMessageOuterClass.QQMessage) {
        val contentList = msg.messageBody.richMsg.msgContentList
        val isFlashPic = contentList.getOrNull(2)?.myCustomField?.mtType == CUSTOM_FIELD_MT_TYPE_FLASH
        if (isFlashPic) {
            AioVisualHelper.showFlashPicTip(msg)
        }
    }

    private fun processC2CRecallPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val opBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()
        val opInfo = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opBytes)

        if (opInfo.info.operatorUid == QQInterfaces.currentUid) return

        val newOpBytes = opInfo.toBuilder().apply {
            info = info.toBuilder().setMsgSeq(INVALID_MSG_SEQ).build()
        }.build().toByteArray()

        param.args[1] = msgPush.updateOperationInfo(newOpBytes).toByteArray()

        AioVisualHelper.showC2CRecallTip(opInfo.info.operatorUid, opInfo.info.msgSeq.toLong())
    }

    private fun processGroupRecallPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val fullOpBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()

        if (fullOpBytes.size <= GROUP_OP_HEADER_SIZE) return

        val bodyBytes = fullOpBytes.copyOfRange(GROUP_OP_HEADER_SIZE, fullOpBytes.size)
        val opInfo = QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo.parseFrom(bodyBytes)

        if (opInfo.info.operatorUid == QQInterfaces.currentUid) return

        val modifiedBody = opInfo.toBuilder().apply {
            msgSeq = INVALID_MSG_SEQ
            info = info.toBuilder().apply {
                msgInfo = msgInfo.toBuilder().setMsgSeq(INVALID_MSG_SEQ).build()
            }.build()
        }.build().toByteArray()

        val newFullBytes = ByteArray(GROUP_OP_HEADER_SIZE + modifiedBody.size)
        System.arraycopy(fullOpBytes, 0, newFullBytes, 0, GROUP_OP_HEADER_SIZE)
        System.arraycopy(modifiedBody, 0, newFullBytes, GROUP_OP_HEADER_SIZE, modifiedBody.size)

        param.args[1] = msgPush.updateOperationInfo(newFullBytes).toByteArray()

        AioVisualHelper.showGroupRecallTip(opInfo)
    }

    private fun extractC2CRecallInfo(msg: QQMessageOuterClass.QQMessage): Pair<String, Long>? {
        return try {
            val opInfo = msg.messageBody.operationInfo
            if (opInfo.isEmpty) return null
            val c2cRecall = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opInfo)
            msg.messageHead.senderUid to c2cRecall.info.msgSeq.toLong()
        } catch (_: Exception) { null }
    }

    private fun MsgPushOuterClass.MsgPush.updateOperationInfo(newBytes: ByteArray): MsgPushOuterClass.MsgPush {
        return this.toBuilder().apply {
            qqMessage = qqMessage.toBuilder().apply {
                messageBody = messageBody.toBuilder().setOperationInfo(ByteString.copyFrom(newBytes)).build()
            }.build()
        }.build()
    }
}
