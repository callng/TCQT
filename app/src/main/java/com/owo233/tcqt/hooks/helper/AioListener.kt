package com.owo233.tcqt.hooks.helper

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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import com.owo233.tcqt.hooks.entries.InfoSyncPush
import com.owo233.tcqt.hooks.entries.MsgPush
import com.owo233.tcqt.hooks.entries.QQMessage

@OptIn(DelicateCoroutinesApi::class, ExperimentalSerializationApi::class)
object AioListener {

    private const val MSG_TYPE_C2C = 528
    private const val SUB_TYPE_C2C_RECALL = 138

    private const val MSG_TYPE_GROUP = 732
    private const val SUB_TYPE_GROUP_RECALL = 17

    private const val MSG_TYPE_FLASH_PIC = 166
    private const val SUB_TYPE_FLASH_PIC = 11

    private const val GROUP_OP_HEADER_SIZE = 7
    private const val INFO_SYNC_PUSH_FLAG_RECALL = 2

    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = ProtoBuf.decodeFromByteArray<MsgPush>(buffer)
        val msg = msgPush.qqMessage ?: return

        val contentInfo = msg.messageContentInfo ?: return
        val msgType = contentInfo.msgType
        val subType = contentInfo.subSeq

        when (msgType to subType) {
            MSG_TYPE_C2C to SUB_TYPE_C2C_RECALL -> processC2CRecallPush(msgPush, param)
            MSG_TYPE_GROUP to SUB_TYPE_GROUP_RECALL -> processGroupRecallPush(msgPush, param)
            MSG_TYPE_FLASH_PIC to SUB_TYPE_FLASH_PIC -> {
                if (GeneratedSettingList.getBoolean(GeneratedSettingList.DISABLE_FLASH_PIC)) {
                    processFlashPicPush(msgPush)
                }
            }
        }
    }

    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = ProtoBuf.decodeFromByteArray<InfoSyncPush>(buffer)

        if (infoSyncPush.pushFlag.toInt() != INFO_SYNC_PUSH_FLAG_RECALL) return

        val recall = infoSyncPush.syncMsgRecall ?: return
        val bodies = recall.syncInfoBody
        if (bodies.isEmpty()) return

        val interceptedC2CRecalls = mutableListOf<Pair<String, Long>>()

        val newBodies = bodies.map { body ->
            val kept = body.msg.filter { m ->
                val ci = m.messageContentInfo ?: return@filter true
                val type = ci.msgType
                val sub = ci.subSeq

                when {
                    type == MSG_TYPE_GROUP && sub == SUB_TYPE_GROUP_RECALL -> false
                    type == MSG_TYPE_C2C && sub == SUB_TYPE_C2C_RECALL -> {
                        extractC2CRecallInfo(m)?.let { interceptedC2CRecalls.add(it) }
                        false
                    }
                    else -> true
                }
            }
            body.copy(msg = kept)
        }

        val newRecall = recall.copy(syncInfoBody = newBodies)
        val newInfoSyncPush = infoSyncPush.copy(syncMsgRecall = newRecall)

        param.args[1] = ProtoBuf.encodeToByteArray(newInfoSyncPush)

        showInterceptedC2CTips(interceptedC2CRecalls)
    }

    private fun processC2CRecallPush(msgPush: MsgPush, param: MethodHookParam) {
        val msg = msgPush.qqMessage ?: return
        val opBytes = msg.messageBody?.operationInfo ?: return

        val operationInfo = ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(opBytes)
        val info = operationInfo.info ?: return

        val operatorUid = info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return

        val newInfo = info.copy(msgSeq = 1)
        val newOp = operationInfo.copy(info = newInfo)
        val newOpBytes = ProtoBuf.encodeToByteArray(newOp)

        val newMsgBody = msg.messageBody.copy(operationInfo = newOpBytes)
        val newMsg = msg.copy(messageBody = newMsgBody)
        val newPush = msgPush.copy(qqMessage = newMsg)

        param.args[1] = ProtoBuf.encodeToByteArray(newPush)

        showC2CRecallTip(operatorUid, info.msgSeq)
    }

    private fun processGroupRecallPush(msgPush: MsgPush, param: MethodHookParam) {
        val msg = msgPush.qqMessage ?: return
        val fullOpBytes = msg.messageBody?.operationInfo ?: return

        if (fullOpBytes.size <= GROUP_OP_HEADER_SIZE) return
        val headerBytes = fullOpBytes.copyOfRange(0, GROUP_OP_HEADER_SIZE)
        val bodyBytes = fullOpBytes.copyOfRange(GROUP_OP_HEADER_SIZE, fullOpBytes.size)

        val operationInfo = ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.GroupRecallOperationInfo>(bodyBytes)
        val info = operationInfo.info ?: return

        val operatorUid = info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return

        val newMsgInfo = info.msgInfo?.copy(msgSeq = 1)
        val newInfo = info.copy(msgInfo = newMsgInfo)
        val newOp = operationInfo.copy(msgSeq = 1, info = newInfo)

        val modifiedBodyBytes = ProtoBuf.encodeToByteArray(newOp)
        val newFullOpBytes = headerBytes + modifiedBodyBytes

        val newMsgBody = msg.messageBody.copy(operationInfo = newFullOpBytes)
        val newMsg = msg.copy(messageBody = newMsgBody)
        val newPush = msgPush.copy(qqMessage = newMsg)

        param.args[1] = ProtoBuf.encodeToByteArray(newPush)

        showGroupRecallTip(operationInfo)
    }

    private fun processFlashPicPush(msgPush: MsgPush) {
        val list = msgPush.qqMessage?.messageBody?.richMsg?.msgContent.orEmpty()
        val isFlashPic = list.any { it.myCustomField?.mtType == 3 }
        if (isFlashPic) {
            val msg = msgPush.qqMessage ?: return
            showFlashPicTip(msg)
        }
    }

    private fun showC2CRecallTip(operatorUid: String, msgSeq: Int) {
        GlobalScope.launchWithCatch {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方想撤回一条")
                msgRef("消息", msgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    private fun showInterceptedC2CTips(list: List<Pair<String, Long>>) {
        if (list.isEmpty()) return
        GlobalScope.launchWithCatch {
            list.forEach { (senderUid, msgSeq) ->
                val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, senderUid)
                LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                    text("对方想撤回一条")
                    msgRef("消息", msgSeq)
                    text(", 已拦截")
                }
            }
        }
    }

    private fun showGroupRecallTip(operationInfo: QQMessage.MessageBody.GroupRecallOperationInfo) {
        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId
            val msgSeq = operationInfo.info?.msgInfo?.msgSeq ?: return@launchWithCatch
            val targetUid = operationInfo.info.msgInfo.senderUid
            val operatorUid = operationInfo.info.operatorUid

            val targetNick = getMemberDisplayName(groupPeerId, targetUid)
            val operatorNick = getMemberDisplayName(groupPeerId, operatorUid)

            val targetUin = ContactHelper.getUinByUidAsync(targetUid)
            val operatorUin = ContactHelper.getUinByUidAsync(operatorUid)

            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, groupPeerId.toString())

            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_GROUP_NOTICE, LocalGrayTips.Align.CENTER) {
                member(operatorUid, operatorUin, operatorNick, "3")
                text("尝试撤回")
                if (targetUid == operatorUid) {
                    text("TA自己")
                } else {
                    member(targetUid, targetUin, targetNick, "3")
                }
                text("的")
                msgRef("消息", msgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    private fun showFlashPicTip(msg: QQMessage) {
        GlobalScope.launchWithCatch {
            delay(233L)
            val operatorUid = msg.messageHead?.senderUid.orEmpty()
            if (operatorUid == QQInterfaces.currentUid) return@launchWithCatch

            val msgSeq = msg.messageContentInfo?.msgSeqId ?: return@launchWithCatch
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)

            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方发送了一条闪照")
                msgRef("消息", msgSeq.toLong())
            }
        }
    }

    private suspend fun getMemberDisplayName(groupPeerId: Long, uid: String): String {
        val uin = ContactHelper.getUinByUidAsync(uid)
        if (uin.isEmpty()) return uid

        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }

    private fun extractC2CRecallInfo(qqMessage: QQMessage): Pair<String, Long>? {
        return runCatching {
            val opBytes = qqMessage.messageBody?.operationInfo ?: return null
            val recall = ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(opBytes)
            val info = recall.info ?: return null
            val senderUid = qqMessage.messageHead?.senderUid ?: return null
            senderUid to info.msgSeq.toLong()
        }.getOrNull()
    }
}
