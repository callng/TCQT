/**
 * Copyright 2024-2026 github@Suzhelan,github@leafmoes
 *
 * suzhelan@gmail.com
 *
 * It is forbidden to use the file and this source code for commercial purposes
 * please let me know before modifying the file and source code
 * If your project is open source, please indicate that this feature is from https://github.com/suzhelan/TimTool
 */
package top.artmoe.inao.item

import com.owo233.tcqt.ext.ifNullOrEmpty
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.LocalGrayTips
import com.owo233.tcqt.hooks.helper.MessageHandler
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.helper.GroupHelper
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import top.artmoe.inao.entries.MsgPush
import top.artmoe.inao.entries.NewSyncPush
import top.artmoe.inao.entries.QQMessage

@Serializable
private data class FriendChatMessageRecall(
    @SerialName("peerUid")
    val peerUid: String,
    @SerialName("msgSeq")
    val msgSeq: Int,
)

@Serializable
private data class GroupChatMessageRecall(
    @SerialName("groupUin")
    val groupUin: String,
    @SerialName("operatorUid")
    val operatorUid: String,
    @SerialName("senderUid")
    val senderUid: String,
    @SerialName("msgSeq")
    val msgSeq: Int,
)

/**
 * 防撤回核心解析
 * by 叶叶,suzhelan
 */
@OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
object NewPreventRetractingMessageCore : MessageHandler {

    private data class ProtoVarInt(
        val value: Int,
        val nextIndex: Int,
    )

    private data class ProtoField(
        val fieldNumber: Int,
        val wireType: Int,
        val rawBytes: ByteArray,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ProtoField

            if (fieldNumber != other.fieldNumber) return false
            if (wireType != other.wireType) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false
            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fieldNumber
            result = 31 * result + wireType
            result = 31 * result + rawBytes.contentHashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    override fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = ProtoBuf.decodeFromByteArray<NewSyncPush>(buffer)
        val syncRecallContent = infoSyncPush.syncRecallContent ?: return
        val syncInfoBody = syncRecallContent.syncInfoBody ?: return
        if (syncInfoBody.isEmpty()) {
            return
        }
        val friendList = mutableListOf<FriendChatMessageRecall>()
        val troopList = mutableListOf<GroupChatMessageRecall>()
        val newSyncInfoBody = syncInfoBody.map { syncInfoBodyBytes ->
            rewriteSyncInfoBody(syncInfoBodyBytes, friendList, troopList)
        }
        if (troopList.isEmpty() && friendList.isEmpty()) {
            return
        }
        val newInfoSyncPush = infoSyncPush.copy(
            syncRecallContent = syncRecallContent.copy(
                syncInfoBody = newSyncInfoBody
            )
        )
        param.args[1] = ProtoBuf.encodeToByteArray(newInfoSyncPush)
        showInterceptedC2CTips(friendList)
        // showInterceptedGroupRecalls(troopList)
    }

    override fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = ProtoBuf.decodeFromByteArray<MsgPush>(buffer)
        //检查messageBody是否为空
        if (msgPush.qqMessage.messageBody == null) {
            return
        }
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq
        val operationInfoByteArray = msg.messageBody.operationInfo

        when (msgType) {
            528 -> if (subType == 138) onC2CRecallByMsgPush(operationInfoByteArray, msgPush, param)
            732 -> if (subType == 17) onGroupRecallByMsgPush(operationInfoByteArray, msgPush, param)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // MsgPush 撤回拦截
    // ═══════════════════════════════════════════════════════════

    private fun onC2CRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPush,
        param: MethodHookParam,
    ) {
        //断言 messageBody不为空
        check(msgPush.qqMessage.messageBody != null)
        val operationInfo =
            ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(
                operationInfoByteArray
            )

        //msg seq
        val recallMsgSeq = operationInfo.info.msgSeq
        //peerUid
        val operatorUid = operationInfo.info.operatorUid

        val newOperationInfoByteArray = ProtoBuf.encodeToByteArray(
            operationInfo.copy(
                info = operationInfo.info.copy(msgSeq = 1)
            )
        )

        val newMsgPush = msgPush.copy(
            qqMessage = msgPush.qqMessage.copy(
                messageBody = msgPush.qqMessage.messageBody.copy(
                    operationInfo = newOperationInfoByteArray
                )
            )
        )

        param.args[1] = ProtoBuf.encodeToByteArray(newMsgPush)
        showC2CRecallTip(operatorUid, recallMsgSeq)
    }

    private fun onGroupRecallByMsgPush(
        operationInfoByteArray: ByteArray, // 1.3.2
        msgPush: MsgPush,
        param: MethodHookParam,
    ) {
        //断言 messageBody不为空
        check(msgPush.qqMessage.messageBody != null)
        val firstPart = operationInfoByteArray.copyOfRange(0, 7) // 1.3.2.5 and 1.3.2.0
        val secondPart = operationInfoByteArray.copyOfRange(7, operationInfoByteArray.size)
        val operationInfo =
            ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.GroupRecallOperationInfo>(secondPart)

        if (operationInfo.info.operatorUid == QQInterfaces.currentUid) {
            return
        }

        val newOperationInfoByteArray = firstPart + ProtoBuf.encodeToByteArray(
            operationInfo.copy(
                msgSeq = 1,
                info = operationInfo.info.copy(
                    msgInfo = operationInfo.info.msgInfo.copy(msgSeq = 1)
                )
            )
        )

        val newMsgPush = msgPush.copy(
            qqMessage = msgPush.qqMessage.copy(
                messageBody = msgPush.qqMessage.messageBody.copy(
                    operationInfo = newOperationInfoByteArray
                )
            )
        )

        param.args[1] = ProtoBuf.encodeToByteArray(newMsgPush)
        showGroupRecallTip(operationInfo)
    }

    // ═══════════════════════════════════════════════════════════
    // C2C 撤回提示
    // ═══════════════════════════════════════════════════════════

    private fun showC2CRecallTip(operatorUid: String, msgSeq: Int) {
        GlobalScope.launchWithCatch {
            showC2CRecallTipInternal(operatorUid, msgSeq)
        }
    }

    private fun showInterceptedC2CTips(list: List<FriendChatMessageRecall>) {
        if (list.isEmpty()) return
        GlobalScope.launchWithCatch {
            list.forEach { showC2CRecallTipInternal(it.peerUid, it.msgSeq) }
        }
    }

    private suspend fun showC2CRecallTipInternal(peerUid: String, msgSeq: Int) {
        val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, peerUid)
        LocalGrayTips.addLocalGrayTip(
            contact,
            JsonGrayBusiId.AIO_AV_C2C_NOTICE,
            LocalGrayTips.Align.CENTER
        ) {
            text("对方想撤回一条")
            msgRef("消息", msgSeq.toLong())
            text("(seq=$msgSeq)")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Group 撤回提示
    // ═══════════════════════════════════════════════════════════

    private fun showGroupRecallTip(operationInfo: QQMessage.MessageBody.GroupRecallOperationInfo) {
        GlobalScope.launchWithCatch {
            showGroupRecallTipInternal(
                groupUin = operationInfo.peerId.toString(),
                operatorUid = operationInfo.info.operatorUid,
                targetUid = operationInfo.info.msgInfo.senderUid,
                msgSeq = operationInfo.info.msgInfo.msgSeq,
            )
        }
    }

    /*private fun showInterceptedGroupRecalls(list: List<GroupChatMessageRecall>) {
        if (list.isEmpty()) return
        GlobalScope.launchWithCatch {
            list.forEach {
                showGroupRecallTipInternal(it.groupUin, it.operatorUid, it.senderUid, it.msgSeq)
            }
        }
    }*/

    private suspend fun showGroupRecallTipInternal(
        groupUin: String,
        operatorUid: String,
        targetUid: String,
        msgSeq: Int,
    ) {
        val groupPeerId = groupUin.toLong()
        val operatorNick = getMemberDisplayName(groupPeerId, operatorUid)
        val targetNick = getMemberDisplayName(groupPeerId, targetUid)
        val operatorUin = ContactHelper.getUinByUidAsync(operatorUid)
        val targetUin = ContactHelper.getUinByUidAsync(targetUid)
        val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, groupUin)

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
            msgRef("消息", msgSeq.toLong())
            text("(seq=$msgSeq)")
        }
    }

    private suspend fun getMemberDisplayName(groupPeerId: Long, uid: String): String {
        val uin = ContactHelper.getUinByUidAsync(uid)
        if (uin.isEmpty()) return uid

        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }

    // ═══════════════════════════════════════════════════════════
    // SyncPush 协议处理
    // ═══════════════════════════════════════════════════════════

    private fun rewriteSyncInfoBody(
        syncInfoBodyBytes: ByteArray,
        friendList: MutableList<FriendChatMessageRecall>,
        troopList: MutableList<GroupChatMessageRecall>,
    ): ByteArray {
        val fields = parseProtoFields(syncInfoBodyBytes) ?: return syncInfoBodyBytes

        val kept = fields.filterNot { field ->
            field.fieldNumber == 8 && field.wireType == 2 &&
                shouldRemoveRecallMessage(field.payload, friendList, troopList)
        }

        return kept.takeIf { it.size < fields.size }
            ?.map { it.rawBytes }
            ?.reduce(ByteArray::plus)
            ?: syncInfoBodyBytes
    }

    private fun parseProtoFields(bytes: ByteArray): List<ProtoField>? {
        val fields = mutableListOf<ProtoField>()
        var index = 0

        while (index < bytes.size) {
            val fieldStart = index
            val key = readVarInt(bytes, index) ?: return null
            index = key.nextIndex
            val fieldNumber = key.value ushr 3
            val wireType = key.value and 0x07

            val (fieldEnd, payload) = when (wireType) {
                0 -> readVarInt(bytes, index)
                    ?.let { it.nextIndex to ByteArray(0) } ?: return null

                1 -> (index + 8).takeIf { it <= bytes.size }
                    ?.let { it to ByteArray(0) } ?: return null

                2 -> {
                    val len = readVarInt(bytes, index) ?: return null
                    val end = len.nextIndex + len.value
                    end.takeIf { it <= bytes.size }
                        ?.let { it to bytes.copyOfRange(len.nextIndex, end) }
                        ?: return null
                }

                5 -> (index + 4).takeIf { it <= bytes.size }
                    ?.let { it to ByteArray(0) } ?: return null

                else -> return null
            }

            fields.add(ProtoField(fieldNumber, wireType, bytes.copyOfRange(fieldStart, fieldEnd), payload))
            index = fieldEnd
        }

        return fields
    }

    private fun shouldRemoveRecallMessage(
        msgBytes: ByteArray,
        friendList: MutableList<FriendChatMessageRecall>,
        troopList: MutableList<GroupChatMessageRecall>,
    ): Boolean {
        val qqMessage = runCatching {
            ProtoBuf.decodeFromByteArray<QQMessage>(msgBytes)
        }.getOrNull() ?: return false
        val messageBody = qqMessage.messageBody ?: return false
        val msgType = qqMessage.messageContentInfo.msgType
        val msgSubType = qqMessage.messageContentInfo.msgSubType

        return when (msgType) {
            528 if msgSubType == 138 -> {
                val c2cRecall = runCatching {
                    ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(
                        messageBody.operationInfo
                    )
                }.getOrNull() ?: return false
                friendList.add(
                    FriendChatMessageRecall(
                        qqMessage.messageHead.senderUid,
                        c2cRecall.info.msgSeq
                    )
                )
                true
            }

            732 if msgSubType == 17 -> {
                if (messageBody.operationInfo.size <= 7) {
                    return false
                }
                val groupRecall = runCatching {
                    ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.GroupRecallOperationInfo>(
                        messageBody.operationInfo.copyOfRange(7, messageBody.operationInfo.size)
                    )
                }.getOrNull() ?: return false

                if (groupRecall.info.operatorUid == QQInterfaces.currentUid) {
                    return false
                }

                troopList.add(
                    GroupChatMessageRecall(
                        groupUin = groupRecall.peerId.toString(),
                        operatorUid = groupRecall.info.operatorUid,
                        senderUid = groupRecall.info.msgInfo.senderUid,
                        msgSeq = groupRecall.info.msgInfo.msgSeq
                    )
                )
                true
            }

            else -> false
        }
    }

    private fun readVarInt(bytes: ByteArray, startIndex: Int): ProtoVarInt? {
        var result = 0
        var shift = 0
        var index = startIndex

        while (index < bytes.size && shift < Int.SIZE_BITS) {
            val byte = bytes[index].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            index++
            if ((byte and 0x80) == 0) {
                return ProtoVarInt(result, index)
            }
            shift += 7
        }
        return null
    }
}
