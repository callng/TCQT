package com.owo233.tcqt.hooks.helper

import com.google.protobuf.UnknownFieldSet
import com.owo233.tcqt.entries.C2CRecallMessage
import com.owo233.tcqt.entries.GroupRecallMessage
import com.owo233.tcqt.entries.Message
import com.owo233.tcqt.entries.MessagePush
import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.helper.GroupHelper
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import kotlinx.io.core.readBytes
import kotlinx.io.core.readUInt
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.text.toLong

object AioListener {

    fun onMsgPush(msgPush: MessagePush): Boolean {
        val msgType = msgPush.msgBody.content.msgType
        val subType = msgPush.msgBody.content.msgSubType
        val msgBody = msgPush.msgBody
        return when(msgType) {
            528 -> when(subType) {
                138 -> onC2CRecall(msgBody.body!!.msgContent)
                else -> false
            }
            732 -> when(subType) {
                17 -> onGroupRecall(msgBody, msgBody.body!!.msgContent)
                else -> false
            }
            else -> false
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalUnsignedTypes::class,
        ExperimentalSerializationApi::class
    )
    private fun onGroupRecall(message: Message, msgContent: ByteArray?): Boolean {
        if (msgContent == null) return false

        val reader = ByteReadPacket(msgContent)
        val buffer = try {
            if (msgContent.size >= 7 && reader.readUInt() == message.msgHead.peerId) {
                reader.discardExact(1)
                reader.readBytes(reader.readShort().toInt())
            } else msgContent
        } finally {
            reader.release()
        }
        val recallData = ProtoBuf.decodeFromByteArray<GroupRecallMessage>(buffer)

        if (recallData.type != 7u || recallData.peerId == 0uL) return false

        val operatorUid = recallData.operation.operatorUid ?: ""

        if (operatorUid == QQInterfaces.app.currentUid) return false

        GlobalScope.launchWithCatch {
            val groupCode = recallData.peerId.toLong()
            val targetUid = recallData.operation.msgInfo?.senderUid ?: ""
            val msgSeq = recallData.operation.msgInfo?.msgSeq ?: 0L
            val target = ContactHelper.getUinByUidAsync(targetUid)
            val operator = ContactHelper.getUinByUidAsync(operatorUid)

            val targetNick = (if (targetUid.isEmpty()) null else GroupHelper.getTroopMemberInfoByUin(groupCode, target.toLong()).getOrNull())?.let {
                it.troopnick.ifNullOrEmpty { it.friendnick }
            } ?: targetUid
            val operatorNick = (if (operatorUid.isEmpty()) null else GroupHelper.getTroopMemberInfoByUin(groupCode, operator.toLong()).getOrNull())?.let {
                it.troopnick.ifNullOrEmpty { it.friendnick }
            } ?: operatorUid

            val contact = ContactHelper.generateContact(
                chatType = MsgConstant.KCHATTYPEGROUP,
                id = groupCode.toString()
            )
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_GROUP_NOTICE, LocalGrayTips.Align.CENTER) {
                member(operatorUid, operator, operatorNick, "3")
                text("想撤回")
                if (targetUid == operatorUid) {
                    text("TA")
                } else {
                    member(targetUid, target, targetNick, "3")
                }
                text("的")
                msgRef("消息", msgSeq)
                text(",已拦截")
            }
        }

        return true
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalSerializationApi::class)
    private fun onC2CRecall(richMsg: ByteArray?): Boolean {
        if (richMsg == null) return false

        GlobalScope.launchWithCatch {
            val recallData = ProtoBuf.decodeFromByteArray<C2CRecallMessage>(richMsg)

            val senderUid = recallData.info.senderUid
            val msgSeq = recallData.info.msgSeq

            if (senderUid == QQInterfaces.app.currentUid) return@launchWithCatch

            val contact = ContactHelper.generateContact(
                chatType = MsgConstant.KCHATTYPEC2C,
                id = senderUid
            )
            LocalGrayTips.addLocalGrayTip(contact, JsonGrayBusiId.AIO_AV_C2C_NOTICE, LocalGrayTips.Align.CENTER) {
                text("对方想撤回一条")
                msgRef("消息", msgSeq)
                text(",已拦截")
            }
        }

        return true
    }

    fun onInfoSyncPush(fieldSet: UnknownFieldSet): Result<UnknownFieldSet> {
        val type = fieldSet.getField(3)
        if (!type.varintList.any { it == 2L }) {
            return Result.success(fieldSet)
        }

        val builder = UnknownFieldSet.newBuilder(fieldSet)
        builder.clearField(8) // 移除content的内容

        val contentsBuilder = UnknownFieldSet.Field.newBuilder()
        val contents = fieldSet.getField(8)
        // 这里可以使用groupList
        // 其它地方只能用lengthDelimitedList？应该是qq不同的业务Proto版本不一样的导致的
        contents.groupList.forEach { content ->
            var isRecallEvent = false
            val bodies = content.getField(4)
            bodies.groupList.forEach { body ->
                val msgs = body.getField(8)
                msgs.groupList.forEach { msg ->
                    val msgHead = msg.getField(2).groupList.first()
                    val msgType = msgHead.getField(1).varintList.first()
                    val msgSubType = msgHead.getField(2).varintList.first()
                    isRecallEvent = (msgType == 528L && msgSubType == 138L) || (msgType == 732L && msgSubType == 17L)
                }
            }
            if (!isRecallEvent) {
                contentsBuilder.addGroup(content)
            }
        }

        builder.addField(8, contentsBuilder.build())
        return Result.success(builder.build())
    }
}
