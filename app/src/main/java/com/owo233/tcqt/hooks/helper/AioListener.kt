package com.owo233.tcqt.hooks.helper

import com.google.protobuf.ByteString
import com.owo233.tcqt.entries.InfoSyncPushOuterClass
import com.owo233.tcqt.entries.MsgPushOuterClass
import com.owo233.tcqt.entries.QQMessageOuterClass
import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.helper.GroupHelper
import com.owo233.tcqt.utils.MethodHookParam
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay

object AioListener {

    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = MsgPushOuterClass.MsgPush.parseFrom(buffer)
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq
        val operationInfoByteArray = msg.messageBody.operationInfo.toByteArray()

        when(msgType) {
            528 -> when (subType) {
                138 -> onC2CRecallByMsgPush(operationInfoByteArray, msgPush, param)
            }
            732 -> when (subType) {
                17 -> onGroupRecallByMsgPush(operationInfoByteArray, msgPush, param)
            }
            166 -> when (subType) { // 好友私聊消息
                11 -> GeneratedSettingList.DISABLE_FLASH_PIC
                    .takeIf { GeneratedSettingList.getBoolean(it) }
                    .let { onC2CFlashPicByMsgPush(buffer, msgPush, param) }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onC2CFlashPicByMsgPush(
        buffer: ByteArray,
        msgPush: MsgPushOuterClass.MsgPush,
        param: MethodHookParam
    ) {
        param.args[1] = buffer

        val contentList = msgPush
            .qqMessage // 1
            .messageBody // 3
            .richMsg // 1
            .msgContentList // 2

        if (contentList.size >= 3 && contentList[2].myCustomField.mtType == 3) {
            GlobalScope.launchWithCatch{ // 闪照消息，由于闪照被视为正常图片，所以添加一条提示
                delay(233L)

                val operatorUid = msgPush.qqMessage.messageHead.senderUid
                if (operatorUid == QQInterfaces.currentUid) return@launchWithCatch

                val msgSeq = msgPush.qqMessage.messageContentInfo.msgSeqId

                val contact = ContactHelper.generateContact(
                    chatType = MsgConstant.KCHATTYPEC2C,
                    id = operatorUid
                )
                LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                    text("对方发送了一条闪照")
                    msgRef("消息", msgSeq.toLong())
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onGroupRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPushOuterClass.MsgPush,
        param: MethodHookParam
    ) {
        val firstPart = operationInfoByteArray.copyOfRange(0, 7)
        val secondPart = operationInfoByteArray.copyOfRange(7, operationInfoByteArray.size)

        val operationInfo = QQMessageOuterClass
            .QQMessage
            .MessageBody
            .GroupRecallOperationInfo.parseFrom(secondPart)

        val operatorUid = operationInfo.info.operatorUid // 操作者UID

        if (operatorUid == QQInterfaces.currentUid) return // 操作者是自己,不处理

        val newOperationInfoByteArray = firstPart + (operationInfo.toBuilder().apply {
            msgSeq = 1
            info = info.toBuilder().apply {
                msgInfo = msgInfo.toBuilder().setMsgSeq(1).build()
            }.build()
        }.build().toByteArray())

        val newMsgPush = msgPush.toBuilder().apply {
            qqMessage = qqMessage.toBuilder().apply {
                messageBody = messageBody.toBuilder().apply {
                    setOperationInfo(ByteString.copyFrom(newOperationInfoByteArray))
                }.build()
            }.build()
        }.build()

        param.args[1] = newMsgPush.toByteArray() // 替换已修改的数据

        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId // 群号
            val recallMsgSeq = operationInfo.info.msgInfo.msgSeq // 消息Seq
            val targetUid = operationInfo.info.msgInfo.senderUid // 被操作者UID
            val targetUin = ContactHelper.getUinByUidAsync(targetUid) // 被操作者UIN
            val operatorUin = ContactHelper.getUinByUidAsync(operatorUid) // 操作者UIN

            // 拿到被撤回消息的发送者昵称(群昵称->QQ昵称->UID)
            val targetNick = if (targetUin.isEmpty()) targetUid else
                GroupHelper.getTroopMemberNickByUin(groupPeerId, targetUin.toLong())
                    ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } } ?: targetUid

            // 拿到操作者的昵称(群昵称->QQ昵称->UID)
            val operatorNick = if (operatorUin.isEmpty()) operatorUid else
                GroupHelper.getTroopMemberNickByUin(groupPeerId, operatorUin.toLong())
                    ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } } ?: operatorUid

            val contact = ContactHelper.generateContact(
                chatType = MsgConstant.KCHATTYPEGROUP,
                id = groupPeerId.toString()
            )

            LocalGrayTips.addLocalGrayTip(
                contact,
                JsonGrayBusiId.AIO_AV_GROUP_NOTICE,
                LocalGrayTips.Align.CENTER
            ) {
                member(operatorUid, operatorUin, operatorNick, "3")
                text("尝试撤回")
                if (targetUid == operatorUid) {
                    text("TA自己")
                } else {
                    member(targetUid, targetUin, targetNick, "3")
                }
                text("的")
                msgRef("消息", recallMsgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onC2CRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPushOuterClass.MsgPush,
        param: MethodHookParam
    ) {
        val operationInfo = QQMessageOuterClass
            .QQMessage
            .MessageBody
            .C2CRecallOperationInfo.parseFrom(operationInfoByteArray)

        val newOperationInfoByteArray = operationInfo.toBuilder().apply {
            info = info.toBuilder().apply {
                msgSeq = 1
            }.build()
        }.build().toByteArray()

        val newMsgPush = msgPush.toBuilder().apply {
            qqMessage = qqMessage.toBuilder().apply {
                messageBody = messageBody.toBuilder().apply {
                    setOperationInfo(
                        ByteString.copyFrom(newOperationInfoByteArray)
                    )
                }.build()
            }.build()
        }.build()

        param.args[1] = newMsgPush.toByteArray()

        GlobalScope.launchWithCatch{
            val operatorUid = operationInfo.info.operatorUid
            if (operatorUid == QQInterfaces.currentUid) return@launchWithCatch

            val recallMsgSeq = operationInfo.info.msgSeq

            val contact = ContactHelper.generateContact(
                chatType = MsgConstant.KCHATTYPEC2C,
                id = operatorUid
            )
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方想撤回一条")
                msgRef("消息", recallMsgSeq.toLong())
                text(",已拦截")
            }
        }
    }

    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = InfoSyncPushOuterClass.InfoSyncPush.parseFrom(buffer)
        infoSyncPush.syncMsgRecall.syncInfoBodyList.forEach { syncInfoBody ->
            syncInfoBody.msgList.forEach { qqMessage ->
                val msgType = qqMessage.messageContentInfo.msgType
                val subType = qqMessage.messageContentInfo.subSeq
                if ((msgType == 732 && subType == 17) || (msgType == 528 && subType == 138)) {
                    val newInfoSyncPush = infoSyncPush.toBuilder().apply {
                        syncMsgRecall = syncMsgRecall.toBuilder().apply {
                            for (i in 0 until syncInfoBodyCount) {
                                setSyncInfoBody(
                                    i, getSyncInfoBody(i).toBuilder().clearMsg().build()
                                )
                            }
                        }.build()
                    }.build()

                    param.args[1] = newInfoSyncPush.toByteArray()
                }
            }
        }
    }
}
