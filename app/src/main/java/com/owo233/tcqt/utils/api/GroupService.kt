package com.owo233.tcqt.utils.api

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.base.hostFunction2
import com.owo233.tcqt.hooks.base.hostUnit
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hook.paramCount
import com.owo233.tcqt.utils.proto2json.ProtoMap
import com.tencent.mobileqq.data.troop.TroopInfo
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qroute.QRouteApi
import com.tencent.mobileqq.troop.api.IBizTroopMemberInfoService
import com.tencent.mobileqq.troop.api.ITroopInfoService
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberShutUpInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole
import com.tencent.qqnt.troopmemberlist.ITroopMemberExtInfoRepoApi
import com.tencent.qqnt.troopmemberlist.impl.TroopMemberExtInfoRepoApiImpl
import com.tencent.relation.common.api.IRelationNTUinAndUidApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import mqq.app.api.IRuntimeService
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
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
            runtime<IBizTroopMemberInfoService>().getTroopMemberNickNoEmpty(groupId, uin)

        ToServiceMsg(
            "mobileqq.service",
            QQInterfaces.currentUin,
            "OidbSvc.0x8fc_2"
        ).apply {
            putWupBuffer(ProtoMap().apply {
                this[1] = 2300
                this[2] = 2
                this[4, 1] = groupId.toLong()
                this[4, 3, 1] = uin.toLong()
                this[4, 3, 5] = title
                this[4, 3, 6] = 4294967295L
                this[4, 3, 7] = troopMemberNickNoEmpty
            }.toByteArray())
            addAttribute("req_pb_protocol_flag", true)
        }.also {
            QQInterfaces.appRuntime.sendToService(it)
            Toasts.success("设置头衔成功")
        }
    }

    fun getGroupInfo(groupId: String): TroopInfo {
        return runtime<ITroopInfoService>().getTroopInfo(groupId)
    }

    suspend fun fetchTroopAdmin(groupId: String) =
        fetchTroopMemberList(groupId, "fetchTroopAdmin")

    suspend fun fetchTroopOwner(groupId: String) =
        fetchTroopMemberList(groupId, "fetchTroopOwner")

    private suspend fun fetchTroopMemberList(
        groupId: String,
        methodName: String
    ): List<String> {
        val result = withTimeoutOrNull(5.seconds) {
            suspendCancellableCoroutine { cont ->
                val callback = Proxy.newProxyInstance(
                    HookEnv.hostClassLoader,
                    arrayOf(hostFunction2)
                ) { _, method, args ->
                    if (method.name == "invoke" && args?.isNotEmpty() == true) {
                        @Suppress("UNCHECKED_CAST")
                        if (args[0] as? Boolean == true) {
                            (args[1] as? List<String>)
                                ?.takeIf { cont.isActive }
                                ?.also { cont.resume(it) }
                        } else {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    hostUnit
                }

                runCatching {
                    TroopMemberExtInfoRepoApiImpl::class.java
                        .declaredMethods.first { it.name == methodName && it.paramCount == 3 }
                        .invoke(
                            api<ITroopMemberExtInfoRepoApi>(),
                            groupId,
                            null,
                            callback
                        )
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        return result ?: listOf()
    }

    fun getUidFromUin(uin: String): String {
        return api<IRelationNTUinAndUidApi>().getUidFromUin(uin)
    }

    fun getUinFromUid(uid: String): String {
        return api<IRelationNTUinAndUidApi>().getUinFromUid(uid)
    }

    private inline fun <reified T : QRouteApi> api(): T {
        return QRoute.api(T::class.java)
    }

    private inline fun <reified T : IRuntimeService> runtime(): T {
        return QQInterfaces.appRuntime.getRuntimeService(T::class.java, "all")
    }
}
