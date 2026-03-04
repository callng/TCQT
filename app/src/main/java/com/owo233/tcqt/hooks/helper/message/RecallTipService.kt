package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.LocalGrayTips
import com.owo233.tcqt.internals.helper.GroupHelper
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import top.artmoe.inao.entries.QQMessageOuterClass

@OptIn(DelicateCoroutinesApi::class)
class RecallTipService(
    private val coroutineScope: CoroutineScope = GlobalScope
) {

    suspend fun getMemberDisplayName(groupPeerId: Long, uid: String): String {
        val uin = ContactHelper.getUinByUidAsync(uid)
        if (uin.isEmpty()) return uid

        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }

    fun showC2CRecallTip(operatorUid: String, msgSeq: Int) {
        coroutineScope.launchWithCatch {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方想撤回一条")
                msgRef("消息", msgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    suspend fun showGroupRecallTip(operationInfo: QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo) {
        val groupPeerId = operationInfo.peerId
        val msgSeq = operationInfo.info.msgInfo.msgSeq
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

    fun showFlashPicTip(operatorUid: String, msgSeq: Long) {
        coroutineScope.launchWithCatch {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方发送了一条闪照")
                msgRef("消息", msgSeq)
            }
        }
    }
}
