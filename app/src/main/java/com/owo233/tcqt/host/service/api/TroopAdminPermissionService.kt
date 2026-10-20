package com.owo233.tcqt.host.service.api

import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberNewExtInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernel.nativeinterface.RcvJoinVerifyMsgReq
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal object TroopAdminPermissionService {

    private val service get() = QQInterfaces.groupService

    suspend fun load(group: Long, target: Long): AdminPermissionSnapshot = coroutineScope {
        val members = async { checkedMembers(group, target) }
        val extended = async { fetchExtended(group, target) }
        val member = members.await()
        val items = extended.await().memberPermissions?.permissionItems
            ?: throw AdminPermissionException("服务器未返回管理员权限")
        val values = items.associateBy { it.permissionItems }
        AdminPermissionSnapshot(
            AdminPermission.entries.associateWith { permission ->
                if (permission == AdminPermission.RECEIVE_VERIFY) {
                    member.memberFlagExt and 0x40 == 0
                } else AdminPermissionProtocol.isEnabled(values[permission.id]?.value)
            },
            values[AdminPermission.KICK.id]?.extra?.let(AdminPermissionProtocol::decodeKickLimit) ?: 0
        )
    }

    suspend fun change(
        group: Long, target: Long, permission: AdminPermission, enabled: Boolean, limit: Int
    ) {
        AdminPermissionProtocol.validateLimit(permission, enabled, limit)
        checkedMembers(group, target) // Role changes after opening the menu must be respected.
        if (permission == AdminPermission.RECEIVE_VERIFY) {
            setReceiveVerify(group, target, enabled)
        } else {
            request(AdminPermissionProtocol.encodeChange(group, target, permission, enabled, limit))
        }
    }

    private suspend fun checkedMembers(group: Long, target: Long): MemberInfo {
        val currentUid = QQInterfaces.currentUid
        val targetUid = GroupService.getUidFromUin(target.toString())
        check(currentUid.isNotBlank() && targetUid.isNotBlank()) { "未能获取群成员 UID" }
        return withTimeout(10.seconds) {
            suspendCancellableCoroutine { continuation ->
                service.getMemberInfoForMqq(group, arrayListOf(currentUid, targetUid), true) { code, msg, result ->
                    val value = runCatching {
                        checkResult(code, msg)
                        val infos = result?.infos ?: error("未返回群成员信息")
                        check(infos[currentUid]?.role == MemberRole.OWNER) { "仅群主可设置管理员权限" }
                        val targetInfo = infos[targetUid] ?: error("未返回目标管理员信息")
                        check(targetInfo.role == MemberRole.ADMIN && targetInfo.uin == target) {
                            "目标成员已不是管理员"
                        }
                        targetInfo
                    }
                    if (continuation.isActive) continuation.resume(value)
                }
            }.getOrThrow()
        }
    }

    private suspend fun fetchExtended(group: Long, target: Long): GroupMemberNewExtInfo =
        withTimeout(10.seconds) {
            suspendCancellableCoroutine { continuation ->
                service.ForceFetchGroupPartMemberNewExtInfo(group, arrayListOf(target)) { code, msg, infos ->
                    val result = runCatching {
                        checkResult(code, msg)
                        infos?.firstOrNull { it.uin == target && it.groupCode == group }
                            ?: error("未返回目标管理员的权限")
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            }.getOrThrow()
        }

    private suspend fun setReceiveVerify(group: Long, target: Long, enabled: Boolean) =
        withTimeout(10.seconds) {
            suspendCancellableCoroutine { continuation ->
                val request = RcvJoinVerifyMsgReq().apply {
                    groupCode = group
                    rptAdminUin = arrayListOf(target)
                    authority = 1
                    operate = if (enabled) 1 else 0
                }
                service.setRcvJoinVerifyMsg(request) { code, msg, response ->
                    val result = runCatching {
                        checkResult(code, msg)
                        check(response?.groupCode == group && response.rptOkUin?.contains(target) == true) {
                            "服务器未确认该管理员的修改"
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            }.getOrThrow()
        }

    private suspend fun request(bytes: ByteArray) = withTimeout(10.seconds) {
        suspendCancellableCoroutine { continuation ->
            PacketHelper.sendRequest(AdminPermissionProtocol.COMMAND, bytes) { response ->
                val result = runCatching { AdminPermissionProtocol.checkResponse(response) }
                if (continuation.isActive) continuation.resume(result)
            }
        }.getOrThrow()
    }

    private fun checkResult(code: Int, message: String?) {
        if (code != 0) throw AdminPermissionException("${message?.ifBlank { null } ?: "请求失败"} ($code)")
    }
}
