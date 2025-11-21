package com.owo233.tcqt.hooks.helper

import com.google.protobuf.ByteString
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
import top.artmoe.inao.entries.InfoSyncPushOuterClass
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

@OptIn(DelicateCoroutinesApi::class)
object AioListener {

    private const val MSG_TYPE_C2C = 528
    private const val SUB_TYPE_C2C_RECALL = 138

    private const val MSG_TYPE_GROUP = 732
    private const val SUB_TYPE_GROUP_RECALL = 17

    private const val MSG_TYPE_FLASH_PIC = 166
    private const val SUB_TYPE_FLASH_PIC = 11

    // 群撤回 OperationInfo 头部偏移量
    private const val GROUP_OP_HEADER_SIZE = 7
    private const val INFO_SYNC_PUSH_FLAG_RECALL = 2

    /**
     * 单条消息推送 (MsgPush)
     */
    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam) {
        val msgPush = MsgPushOuterClass.MsgPush.parseFrom(buffer)
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq

        when (msgType to subType) {
            MSG_TYPE_C2C to SUB_TYPE_C2C_RECALL -> {
                processC2CRecallPush(msgPush, param)
            }
            MSG_TYPE_GROUP to SUB_TYPE_GROUP_RECALL -> {
                processGroupRecallPush(msgPush, param)
            }
            MSG_TYPE_FLASH_PIC to SUB_TYPE_FLASH_PIC -> {
                if (GeneratedSettingList.getBoolean(GeneratedSettingList.DISABLE_FLASH_PIC)) {
                    processFlashPicPush(msgPush)
                }
            }
        }
    }

    /**
     * 消息同步推送 (InfoSyncPush)
     */
    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam) {
        val infoSyncPush = InfoSyncPushOuterClass.InfoSyncPush.parseFrom(buffer)

        // 只有 flag=2 才包含可能的撤回逻辑
        if (infoSyncPush.pushFlag != INFO_SYNC_PUSH_FLAG_RECALL) return

        val interceptedC2CRecalls = mutableListOf<Pair<String, Long>>()

        val newInfoSyncPush = infoSyncPush.toBuilder().apply {
            syncMsgRecall = syncMsgRecall.toBuilder().apply {
                for (i in 0 until syncInfoBodyCount) {
                    val bodyBuilder = getSyncInfoBody(i).toBuilder()

                    // 保留非撤回消息，或者非目标类型的撤回消息
                    val keptMessages = bodyBuilder.msgList.filter { msg ->
                        val type = msg.messageContentInfo.msgType
                        val sub = msg.messageContentInfo.subSeq

                        when (type) {
                            // 拦截群撤回：直接过滤掉
                            MSG_TYPE_GROUP if sub == SUB_TYPE_GROUP_RECALL -> false

                            // 拦截私聊撤回：记录信息并过滤掉
                            MSG_TYPE_C2C if sub == SUB_TYPE_C2C_RECALL -> {
                                extractC2CRecallInfo(msg)?.let { interceptedC2CRecalls.add(it) }
                                false
                            }

                            // 其他消息保留
                            else -> true
                        }
                    }

                    setSyncInfoBody(i, bodyBuilder.clearMsg().addAllMsg(keptMessages).build())
                }
            }.build()
        }.build()

        param.args[1] = newInfoSyncPush.toByteArray()

        // 批量触发撤回提示
        showInterceptedC2CTips(interceptedC2CRecalls)
    }

    // 业务逻辑方法

    private fun processC2CRecallPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val opInfoBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()
        val operationInfo = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opInfoBytes)

        val operatorUid = operationInfo.info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return // 操作者是自己则不拦截

        // 使撤回失效
        val newOpInfoBytes = operationInfo.toBuilder().apply {
            info = info.toBuilder().setMsgSeq(1).build()
        }.build().toByteArray()

        param.args[1] = msgPush.updateOperationInfo(newOpInfoBytes).toByteArray()

        // 触发撤回提示
        showC2CRecallTip(operatorUid, operationInfo.info.msgSeq)
    }

    private fun processGroupRecallPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam) {
        val fullOpBytes = msgPush.qqMessage.messageBody.operationInfo.toByteArray()

        // 分离头部和主体 (Header 7 bytes + Protobuf Body)
        if (fullOpBytes.size <= GROUP_OP_HEADER_SIZE) return
        val headerBytes = fullOpBytes.copyOfRange(0, GROUP_OP_HEADER_SIZE)
        val bodyBytes = fullOpBytes.copyOfRange(GROUP_OP_HEADER_SIZE, fullOpBytes.size)

        val operationInfo = QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo.parseFrom(bodyBytes)

        val operatorUid = operationInfo.info.operatorUid
        if (operatorUid == QQInterfaces.currentUid) return // 操作者是自己则不拦截

        // 使撤回失效
        val modifiedBodyBytes = operationInfo.toBuilder().apply {
            msgSeq = 1
            info = info.toBuilder().apply {
                msgInfo = msgInfo.toBuilder().setMsgSeq(1).build()
            }.build()
        }.build().toByteArray()

        // 重新拼接 Header + Modified Body
        val newFullOpBytes = headerBytes + modifiedBodyBytes

        param.args[1] = msgPush.updateOperationInfo(newFullOpBytes).toByteArray()

        // 触发撤回提示
        showGroupRecallTip(operationInfo)
    }

    private fun processFlashPicPush(msgPush: MsgPushOuterClass.MsgPush) {
        val contentList = msgPush.qqMessage.messageBody.richMsg.msgContentList

        // 检查是否包含闪照特定字段 (mtType == 3)
        val isFlashPic = contentList.getOrNull(2)?.myCustomField?.mtType == 3

        if (isFlashPic) {
            showFlashPicTip(msgPush.qqMessage)
        }
    }

    // 灰条提示方法

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

    private fun showGroupRecallTip(operationInfo: QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo) {
        GlobalScope.launchWithCatch {
            val groupPeerId = operationInfo.peerId
            val recallMsgSeq = operationInfo.info.msgInfo.msgSeq
            val targetUid = operationInfo.info.msgInfo.senderUid
            val operatorUid = operationInfo.info.operatorUid

            // 获取昵称
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
                msgRef("消息", recallMsgSeq.toLong())
                text(", 已拦截")
            }
        }
    }

    private fun showFlashPicTip(msg: QQMessageOuterClass.QQMessage) {
        GlobalScope.launchWithCatch {
            delay(233L)
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

    // 工具方法

    /**
     * 获取群成员显示名称 (群名片 -> 昵称 -> UID)
     */
    private suspend fun getMemberDisplayName(groupPeerId: Long, uid: String): String {
        val uin = ContactHelper.getUinByUidAsync(uid)
        if (uin.isEmpty()) return uid

        return GroupHelper.getTroopMemberNickByUin(groupPeerId, uin.toLong())
            ?.let { it.troopNick.ifNullOrEmpty { it.friendNick } }
            ?: uid
    }

    /**
     * 提取 C2C 撤回信息
     */
    private fun extractC2CRecallInfo(qqMessage: QQMessageOuterClass.QQMessage): Pair<String, Long>? {
        return try {
            val opInfo = qqMessage.messageBody.operationInfo
            val c2cRecall = QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opInfo)
            val msgSeq = c2cRecall.info.msgSeq.toLong()
            val senderUid = qqMessage.messageHead.senderUid
            senderUid to msgSeq
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 快速替换 MsgPush 中的 OperationInfo
     */
    private fun MsgPushOuterClass.MsgPush.updateOperationInfo(newBytes: ByteArray): MsgPushOuterClass.MsgPush {
        return this.toBuilder().apply {
            qqMessage = qqMessage.toBuilder().apply {
                messageBody = messageBody.toBuilder().apply {
                    setOperationInfo(ByteString.copyFrom(newBytes))
                }.build()
            }.build()
        }.build()
    }
}
