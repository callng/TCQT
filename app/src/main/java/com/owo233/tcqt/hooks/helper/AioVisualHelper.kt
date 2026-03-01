package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.helper.GroupHelper
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import top.artmoe.inao.entries.QQMessageOuterClass

@OptIn(DelicateCoroutinesApi::class)
object AioVisualHelper {

    fun showC2CRecallTip(operatorUid: String, msgSeq: Long) {
        GlobalScope.launchWithCatch {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方想撤回一条")
                msgRef("消息", msgSeq)
                text(", 已拦截")
            }
        }
    }

    fun showInterceptedC2CTips(list: List<Pair<String, Long>>) {
        if (list.isEmpty()) return
        list.forEach { (senderUid, msgSeq) ->
            showC2CRecallTip(senderUid, msgSeq)
        }
    }

    fun showGroupRecallTip(operationInfo: QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo) {
        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId
            val msgInfo = operationInfo.info.msgInfo
            val targetUid = msgInfo.senderUid
            val operatorUid = operationInfo.info.operatorUid

            val targetUinDef = async { ContactHelper.getUinByUidAsync(targetUid) }
            val operatorUinDef = async { ContactHelper.getUinByUidAsync(operatorUid) }

            val targetUin = targetUinDef.await()
            val operatorUin = operatorUinDef.await()

            val targetNickDef = async { getMemberDisplayName(groupPeerId, targetUid, targetUin) }
            val operatorNickDef = async { getMemberDisplayName(groupPeerId, operatorUid, operatorUin) }

            val targetNick = targetNickDef.await()
            val operatorNick = operatorNickDef.await()

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
                msgRef("消息", msgInfo.msgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    fun showFlashPicTip(msg: QQMessageOuterClass.QQMessage) {
        GlobalScope.launchWithCatch {
            delay(300L)
            val operatorUid = msg.messageHead.senderUid
            if (operatorUid == QQInterfaces.currentUid) return@launchWithCatch

            val msgSeq = msg.messageContentInfo.msgSeqId
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)

            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方发送了一条闪照")
                msgRef("消息", msgSeq.toLong())
            }
        }
    }

    private suspend fun getMemberDisplayName(groupPeerId: Long, uid: String, uin: String): String {
        if (uin.isEmpty()) return uid
        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }
}
