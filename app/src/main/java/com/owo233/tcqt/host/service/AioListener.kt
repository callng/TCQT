package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.ifNullOrEmpty
import com.owo233.tcqt.core.env.launchWithCatch
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant

object AioListener : MessageHandler {

    override fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val result = RecallPacketParser.msgPush(buffer, QQInterfaces.currentUid)
        if (result.recalls.isEmpty()) return
        if (result.blockPush) param.result = null else param.args[1] = result.bytes
        notifyRecalls(result.recalls)
    }

    override fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val result = RecallPacketParser.infoSyncPush(buffer, QQInterfaces.currentUid)
        if (result.recalls.isEmpty()) return
        param.args[1] = result.bytes
        notifyRecalls(result.recalls)
    }

    private fun notifyRecalls(recalls: List<RecallPacketParser.Recall>) {
        recalls.distinct().forEach { recall ->
            if (recall.group) {
                if (RecallManager.markGroup(recall.peerId, recall.msgSeq)) showGroupRecallTip(recall)
            } else {
                if (RecallManager.markC2C(recall.peerId, recall.msgSeq)) showC2CRecallTip(recall)
            }
        }
    }

    private fun showC2CRecallTip(recall: RecallPacketParser.Recall) {
        if (!AntiRecallConfig.isGrayTipEnabled()) return
        val account = QQInterfaces.currentUid
        ModuleScope.launchWithCatch {
            if (QQInterfaces.currentUid != account) return@launchWithCatch
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, recall.peerId)
            val target = RecallTargetResolver.resolve(recall, contact)
            if (QQInterfaces.currentUid != account) return@launchWithCatch
            target.msgSeq?.let { RecallManager.markC2C(recall.peerId, it) }
            logTarget(recall, target)
            LocalGrayTips.addLocalGrayTip(
                contact,
                JsonGrayBusiId.AIO_AV_C2C_NOTICE,
                LocalGrayTips.Align.CENTER
            ) {
                text("对方想撤回一条")
                recallMessage(target)
            }
        }
    }

    private fun showGroupRecallTip(recall: RecallPacketParser.Recall) {
        if (!AntiRecallConfig.isGrayTipEnabled()) return
        val account = QQInterfaces.currentUid
        ModuleScope.launchWithCatch {
            if (QQInterfaces.currentUid != account) return@launchWithCatch
            val groupPeerId = recall.peerId.toLong()
            val targetUid = recall.senderUid
            val operatorUid = recall.operatorUid

            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, recall.peerId)
            val target = RecallTargetResolver.resolve(recall, contact)
            if (QQInterfaces.currentUid != account) return@launchWithCatch
            target.msgSeq?.let { RecallManager.markGroup(recall.peerId, it) }
            logTarget(recall, target)

            val targetNick = getMemberDisplayName(groupPeerId, targetUid)
            val operatorNick = getMemberDisplayName(groupPeerId, operatorUid)

            val targetUin = ContactHelper.getUinByUidAsync(targetUid)
            val operatorUin = ContactHelper.getUinByUidAsync(operatorUid)
            if (QQInterfaces.currentUid != account) return@launchWithCatch

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
                recallMessage(target)
            }
        }
    }

    private fun LocalGrayTips.Builder.recallMessage(target: RecallTargetLookup.Result) {
        val seq = target.msgSeq
        if (seq != null) msgRef("消息", seq) else text("消息")
        text(when (target.state) {
            RecallTargetLookup.State.FOUND -> ", 已拦截"
            RecallTargetLookup.State.MISSING -> ", 已拦截（原消息暂未在本地找到）"
            RecallTargetLookup.State.UNAVAILABLE -> ", 已拦截（原消息暂无法确认）"
        })
    }

    private fun logTarget(recall: RecallPacketParser.Recall, target: RecallTargetLookup.Result) {
        // No message text or account identifiers; available in exported logs in release too.
        LogUtils.xposedNoFilter.i(
            "AntiRecallTarget group=${recall.group} seq=${recall.msgSeq} " +
                "senderSeq=${recall.senderMsgSeq} lookup=${target.state} localSeq=${target.msgSeq} ${target.detail}"
        )
    }

    private suspend fun getMemberDisplayName(groupPeerId: Long, uid: String): String {
        val uin = ContactHelper.getUinByUidAsync(uid)
        if (uin.isEmpty()) return uid

        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }
}
