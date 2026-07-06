package com.owo233.tcqt.utils.api

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.api.packet.PacketHelper
import com.owo233.tcqt.utils.proto2json.ProtoList
import com.owo233.tcqt.utils.proto2json.ProtoMap
import com.owo233.tcqt.utils.proto2json.ProtoUtils
import com.owo233.tcqt.utils.proto2json.asInt
import com.owo233.tcqt.utils.proto2json.asLong
import com.owo233.tcqt.utils.proto2json.asMap
import com.tencent.mobileqq.data.troop.TroopInfo
import com.tencent.mobileqq.troop.api.IBizTroopMemberInfoService
import com.tencent.mobileqq.troop.api.ITroopInfoService
import com.tencent.mobileqq.troop.api.ITroopMemberInfoService
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberShutUpInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole
import com.tencent.relation.common.api.IRelationNTUinAndUidApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal object GroupService {

    private val service get() = QQInterfaces.groupService

    fun setGroupShutUp(groupId: String, isEnable: Boolean) {
        service.setGroupShutUp(groupId.toLong(), isEnable) { errCode, errMsg ->
            val sucMsg = if (isEnable) "已开启全员禁言" else "已关闭全员禁言"
            val failMsg = if (isEnable) "开启全员禁言失败" else "关闭全员禁言失败"
            if (errCode != 0) {
                Toasts.error("$failMsg, $errMsg ($errCode)")
            } else {
                Toasts.success(sucMsg)
            }
        }
    }

    fun setMemberShutUp(groupId: String, uin: String, time: Int) {
        val info = arrayListOf(
            GroupMemberShutUpInfo().apply {
                this.uid = getUidFromUin(uin)
                this.timeStamp = time
            }
        )

        service.setMemberShutUp(groupId.toLong(), info) { errCode, errMsg ->
            val sucMsg = if (time > 0) "设置禁言成功" else "取消禁言成功"
            val failMsg = if (time > 0) "设置禁言失败" else "取消禁言失败"
            if (errCode != 0) {
                Toasts.error("$failMsg, $errMsg ($errCode)")
            } else {
                Toasts.success(sucMsg)
            }
        }
    }

    fun modifyMemberRole(groupId: String, uin: String, isEnable: Boolean) {
        if (HookEnv.isTim()) {
            ToServiceMsg(
                "mobileqq.service",
                QQInterfaces.currentUin,
                "OidbSvc.0x55c_1"
            ).apply {
                putWupBuffer(ByteArrayOutputStream().apply {
                    write(0x08)
                    write(0xDC)
                    write(0x0A)
                    write(0x10)
                    write(0x01)
                    write(0x22)
                    write(0x09)
                    write(ByteBuffer.allocate(9).apply {
                        putInt(groupId.toLong().toInt())
                        putInt(uin.toLong().toInt())
                        put((if (isEnable) 1 else 0).toByte())
                    }.array())
                }.toByteArray())
                addAttribute("req_pb_protocol_flag", true)
            }.also {
                QQInterfaces.appRuntime.sendToService(it)
                val sucMsg = if (isEnable) "设置管理员身份成功" else "取消管理员身份成功"
                Toasts.success(sucMsg)
            }

            return
        }

        val role = if (isEnable) MemberRole.ADMIN else MemberRole.MEMBER

        service.modifyMemberRole(
            groupId.toLong(),
            getUidFromUin(uin),
            role
        ) { errCode, errMsg ->
            val sucMsg = if (isEnable) "设置管理员身份成功" else "取消管理员身份成功"
            val failMsg = if (isEnable) "设置管理员身份失败" else "取消管理员身份失败"
            if (errCode != 0) {
                Toasts.error("$failMsg, $errMsg ($errCode)")
            } else {
                Toasts.success(sucMsg)
            }
        }
    }

    fun kickMember(groupId: String, uin: String, isBlock: Boolean) {
        val uids = arrayListOf(getUidFromUin(uin))

        service.kickMember(
            groupId.toLong(),
            uids,
            isBlock,
            ""
        ) { errCode, errMsg, _ ->
            val sucMsg = if (isBlock) "已踢出并拉黑" else "已踢出"
            val failMsg = if (isBlock) "踢出并拉黑失败" else "踢出失败"
            if (errCode != 0) {
                Toasts.error("$failMsg, $errMsg ($errCode)")
            } else {
                Toasts.success(sucMsg)
            }
        }
    }

    fun modifyMemberCardName(groupId: String, uin: String, name: String) {
        service.modifyMemberCardName(
            groupId.toLong(),
            getUidFromUin(uin),
            name
        ) { errCode, errMsg ->
            val sucMsg = "修改名片成功"
            val failMsg = "修改名片失败"
            if (errCode != 0) {
                Toasts.error("$failMsg, $errMsg ($errCode)")
            } else {
                Toasts.success(sucMsg)
            }
        }
    }

    fun setMemberTitle(groupId: String, uin: String, title: String) {
        val troopMemberNickNoEmpty =
            QQInterfaces.runtime<IBizTroopMemberInfoService>().getTroopMemberNickNoEmpty(groupId, uin)

        ToServiceMsg(
            "mobileqq.service",
            QQInterfaces.currentUin,
            "OidbSvc.0x8fc_2"
        ).apply {
            putWupBuffer(ProtoMap().apply {
                this[1] = 2300
                this[2] = 2
                this[4, 1] = groupId.toULong()
                this[4, 3, 1] = uin.toULong()
                this[4, 3, 5] = title
                this[4, 3, 6] = UInt.MAX_VALUE
                this[4, 3, 7] = troopMemberNickNoEmpty
            }.toByteArray())
            addAttribute("req_pb_protocol_flag", true)
        }.also {
            QQInterfaces.appRuntime.sendToService(it)
            Toasts.success("设置头衔成功")
        }
    }

    fun getGroupInfo(groupId: String): TroopInfo {
        return QQInterfaces.runtime<ITroopInfoService>().getTroopInfo(groupId)
    }

    suspend fun getMemberTitle(groupId: String, uin: String): String {
        return withTimeoutOrNull(5.seconds) {
            suspendCancellableCoroutine { cont ->
                val cacheInfo = QQInterfaces.runtime<ITroopMemberInfoService>()
                    .getTroopMemberFromCacheOrFetchAsync(
                        groupId, uin, "AIONickBlockApiImpl-level"
                    ) { info ->
                        if (cont.isActive) {
                            cont.resume(info.specialTitleInfo?.specialTitle ?: "")
                        }
                    }

                if (cacheInfo != null) {
                    cont.resume(cacheInfo.specialTitleInfo?.specialTitle ?: "")
                }
            }
        } ?: ""
    }

    suspend fun getGroupAdminList(groupId: String): List<String> {
        val buffer = ProtoMap().apply {
            this[1] = 2201
            this[2] = 0
            this[4, 1] = groupId.toULong()
            this[4, 2] = 0
            this[4, 3] = 2
            this[4, 5, 1] = 0
            this[4, 5, 18] = 0
            this[4, 6] = 24
            this[4, 7] = 2
            this[12] = 1
        }.toByteArray()

        return withTimeoutOrNull(5.seconds) {
            suspendCancellableCoroutine { cont ->
                // TIM 用 OidbSvc.0x899_1 会炸掉! QQ 则不会. 为了兼容 TIM 用 OidbSvcTrpcTcp.0x899_0
                PacketHelper.sendRequest("OidbSvcTrpcTcp.0x899_0", buffer) { onResponse ->
                    if (onResponse.isEmpty()) {
                        if (cont.isActive) cont.resume(emptyList())
                        return@sendRequest
                    }

                    val resp = ProtoUtils.decodeFromByteArray(onResponse)
                    if (!resp.has(3) || resp[3].asInt != 0) {
                        Toasts.error("getGroupAdminList err(${resp[3].asInt})")
                        if (cont.isActive) cont.resume(emptyList())
                        return@sendRequest
                    }

                    if (!resp.has(4, 4)) {
                        Toasts.error("getGroupAdminList is empty")
                        if (cont.isActive) cont.resume(emptyList())
                        return@sendRequest
                    }

                    val members = when (val item = resp[4, 4]) {
                        is ProtoList -> item.value
                        else -> listOf(item)
                    }
                    val owner = mutableListOf<String>()
                    val admins = mutableListOf<String>()

                    members.forEach { member ->
                        val map = member.asMap
                        val uin = map[1].asLong.toString()
                        when (map[18].asInt) {
                            1 -> owner.add(uin)
                            2 -> admins.add(uin)
                        }
                    }

                    if (cont.isActive) cont.resume(owner + admins)
                }
            }
        } ?: emptyList()
    }

    fun getUidFromUin(uin: String): String {
        return QQInterfaces.api<IRelationNTUinAndUidApi>().getUidFromUin(uin)
    }

    fun getUinFromUid(uid: String): String {
        return QQInterfaces.api<IRelationNTUinAndUidApi>().getUinFromUid(uid)
    }
}
