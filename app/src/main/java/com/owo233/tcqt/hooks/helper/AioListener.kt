package com.owo233.tcqt.hooks.helper

import com.google.protobuf.ByteString
import com.owo233.tcqt.entries.InfoSyncPushOuterClass
import com.owo233.tcqt.entries.MsgPushOuterClass
import com.owo233.tcqt.entries.QQMessageOuterClass
import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.helper.GroupHelper
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import de.robv.android.xposed.XC_MethodHook
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlin.text.toLong

object AioListener {

    fun handleMsgPush(buffer: ByteArray, param: XC_MethodHook.MethodHookParam) {
        val msgPush = MsgPushOuterClass.MsgPush.parseFrom(buffer)
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val msgSubType = msg.messageContentInfo.msgSubType
        val operationInfoByteArray = msg.messageBody.operationInfo.toByteArray()

        when(msgType) {
            528 -> when (msgSubType) {
                138 -> onC2CRecallByMsgPush(operationInfoByteArray, msgPush, param)
            }
            732 -> when (msgSubType) {
                17 -> onGroupRecallByMsgPush(operationInfoByteArray, msgPush, param)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onGroupRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPushOuterClass.MsgPush,
        param: XC_MethodHook.MethodHookParam
    ) {
        val firstPart = operationInfoByteArray.copyOfRange(0, 7)
        val secondPart = operationInfoByteArray.copyOfRange(7, operationInfoByteArray.size)

        val operationInfo = QQMessageOuterClass
            .QQMessage
            .MessageBody
            .GroupRecallOperationInfo.parseFrom(secondPart)

        val newOperationInfoByteArray = firstPart + (operationInfo.toBuilder().apply {
            msgSeq = 1
            info = info.toBuilder().apply {
                msgInfo = msgInfo.toBuilder().setMsgSeq(1).build()
            }.build()
        }.build().toByteArray())

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

        val operatorUid = operationInfo.info.operatorUid
        if (operatorUid == QQInterfaces.app.currentUid) return

        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId // 群号
            val targetUid = operationInfo.info.msgInfo.senderUid //操作目标UID
            val recallMsgSeq = operationInfo.info.msgInfo.msgSeq // 撤回消息序号

            val targetUin = ContactHelper.getUinByUidAsync(targetUid) // 操作目标UIN
            val operatorUin = ContactHelper.getUinByUidAsync(operatorUid) // 操作者UIN

            val targetNick = (if (targetUid.isEmpty()) null else GroupHelper.getTroopMemberInfoByUin(groupPeerId, targetUin.toLong()).getOrNull())?.let {
                it.nickInfo.troopNick.ifNullOrEmpty { it.nickInfo.friendNick }
            } ?: targetUid // 被撤回账号的昵称,优先为群昵称
            val operatorNick = (if (operatorUid.isEmpty()) null else GroupHelper.getTroopMemberInfoByUin(groupPeerId, operatorUin.toLong()).getOrNull())?.let {
                it.nickInfo.troopNick.ifNullOrEmpty { it.nickInfo.friendNick }
            } ?: operatorUid // 操作者的昵称,优先为群昵称

            val contact = ContactHelper.generateContact(
                chatType = MsgConstant.KCHATTYPEGROUP,
                id = groupPeerId.toString()
            )

            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_GROUP_NOTICE, LocalGrayTips.Align.CENTER) {
                member(operatorUid, operatorUin, operatorNick, "3")
                text("想撤回")
                if (targetUid == operatorUid) {
                    text("TA自己")
                } else {
                    member(targetUid, targetUin, targetNick, "3")
                }
                text("的")
                msgRef("消息", recallMsgSeq.toLong())
                text(",已拦截")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onC2CRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPushOuterClass.MsgPush,
        param: XC_MethodHook.MethodHookParam
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
            if (operatorUid == QQInterfaces.app.currentUid) return@launchWithCatch

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

    fun handleInfoSyncPush(buffer: ByteArray, param: XC_MethodHook.MethodHookParam) {
        val infoSyncPush = InfoSyncPushOuterClass.InfoSyncPush.parseFrom(buffer)
        infoSyncPush.syncMsgRecall.syncInfoBodyList.forEach { syncInfoBody ->
            syncInfoBody.msgList.forEach { qqMessage ->
                val msgType = qqMessage.messageContentInfo.msgType
                val msgSubType = qqMessage.messageContentInfo.msgSubType
                if ((msgType == 732 && msgSubType == 17) || (msgType == 528 && msgSubType == 138)) {
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
