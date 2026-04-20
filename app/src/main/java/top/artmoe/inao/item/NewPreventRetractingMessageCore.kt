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
import java.io.ByteArrayOutputStream

@Serializable
data class FriendChatMessageRecall(
    @SerialName("peerUid")
    val peerUid: String,
    @SerialName("msgSeq")
    val msgSeq: Int,
)

@Serializable
data class GroupChatMessageRecall(
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
        showInterceptedGroupRecalls(troopList)
    }

    private fun showInterceptedC2CTips(list: List<FriendChatMessageRecall>) {
        if (list.isEmpty()) return
        GlobalScope.launchWithCatch {
            list.forEach { recall ->
                val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, recall.peerUid)
                LocalGrayTips.addLocalGrayTip(
                    contact,
                    JsonGrayBusiId.AIO_AV_C2C_NOTICE,
                    LocalGrayTips.Align.CENTER
                ) {
                    text("对方想撤回一条")
                    msgRef("消息", recall.msgSeq.toLong())
                    text("(seq=${recall.msgSeq}), 已拦截")
                }
            }
        }
    }

    private fun showInterceptedGroupRecalls(list: List<GroupChatMessageRecall>) {
        if (list.isEmpty()) return
        GlobalScope.launchWithCatch {
            list.forEach { recall ->
                val groupPeerId = recall.groupUin.toLong()
                val operatorUid = recall.operatorUid
                val targetUid = recall.senderUid

                val operatorNick = getMemberDisplayName(groupPeerId, operatorUid)
                val targetNick = getMemberDisplayName(groupPeerId, targetUid)

                val operatorUin = ContactHelper.getUinByUidAsync(operatorUid)
                val targetUin = ContactHelper.getUinByUidAsync(targetUid)

                val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, recall.groupUin)

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
                    msgRef("消息", recall.msgSeq.toLong())
                    text("(seq=${recall.msgSeq}), 已拦截")
                }
            }
        }
    }

    private fun rewriteSyncInfoBody(
        syncInfoBodyBytes: ByteArray,
        friendList: MutableList<FriendChatMessageRecall>,
        troopList: MutableList<GroupChatMessageRecall>,
    ): ByteArray {
        val output = ByteArrayOutputStream(syncInfoBodyBytes.size)
        var index = 0
        var changed = false

        while (index < syncInfoBodyBytes.size) {
            val fieldStart = index
            val key = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
            index = key.nextIndex
            val fieldNumber = key.value ushr 3
            val wireType = key.value and 0x07

            val fieldEnd = when (wireType) {
                0 -> readVarInt(syncInfoBodyBytes, index)?.nextIndex ?: return syncInfoBodyBytes
                1 -> (index + 8).takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                2 -> {
                    val length = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
                    val payloadEnd = length.nextIndex + length.value
                    payloadEnd.takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                }

                5 -> (index + 4).takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                else -> return syncInfoBodyBytes
            }

            if (fieldNumber == 8 && wireType == 2) {
                val length = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
                val payloadStart = length.nextIndex
                val payloadEnd = payloadStart + length.value
                val msgBytes = syncInfoBodyBytes.copyOfRange(payloadStart, payloadEnd)
                if (shouldRemoveRecallMessage(msgBytes, friendList, troopList)) {
                    changed = true
                } else {
                    output.write(syncInfoBodyBytes, fieldStart, fieldEnd - fieldStart)
                }
            } else {
                output.write(syncInfoBodyBytes, fieldStart, fieldEnd - fieldStart)
            }
            index = fieldEnd
        }

        return if (changed) output.toByteArray() else syncInfoBodyBytes
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

                // 当前账号的操作（可能在其他设备上），不拦截
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

        // 当前账号的操作（可能在其他设备上），不拦截
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

    private fun showC2CRecallTip(operatorUid: String, msgSeq: Int) {
        if (operatorUid == QQInterfaces.currentUid) return
        GlobalScope.launchWithCatch {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEC2C, operatorUid)
            LocalGrayTips.addLocalGrayTip(
                contact,
                JsonGrayBusiId.AIO_AV_C2C_NOTICE,
                LocalGrayTips.Align.CENTER
            ) {
                text("对方想撤回一条")
                msgRef("消息", msgSeq.toLong())
                text("(seq=$msgSeq), 已拦截")
            }
        }
    }

    private fun showGroupRecallTip(operationInfo: QQMessage.MessageBody.GroupRecallOperationInfo) {
        if (operationInfo.info.operatorUid == QQInterfaces.currentUid) return
        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId
            val msgInfo = operationInfo.info.msgInfo
            val targetUid = msgInfo.senderUid
            val operatorUid = operationInfo.info.operatorUid

            val targetNick = getMemberDisplayName(groupPeerId, targetUid)
            val operatorNick = getMemberDisplayName(groupPeerId, operatorUid)

            val targetUin = ContactHelper.getUinByUidAsync(targetUid)
            val operatorUin = ContactHelper.getUinByUidAsync(operatorUid)

            val contact =
                ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, groupPeerId.toString())

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
                msgRef("消息", msgInfo.msgSeq.toLong())
                text("(seq=${msgInfo.msgSeq}), 已拦截")
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
}
