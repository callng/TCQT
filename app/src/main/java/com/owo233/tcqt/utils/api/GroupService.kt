package com.owo233.tcqt.utils.api

import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.QQInterfaces
import com.tencent.biz.ProtoServlet
import com.tencent.common.app.BaseApplicationImpl
import com.tencent.mobileqq.data.troop.TroopInfo
import com.tencent.mobileqq.pb.ByteStringMicro
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qroute.QRouteApi
import com.tencent.mobileqq.troop.api.IBizTroopMemberInfoService
import com.tencent.mobileqq.troop.api.ITroopInfoService
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberShutUpInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole
import com.tencent.relation.common.api.IRelationNTUinAndUidApi
import mqq.app.NewIntent
import mqq.app.api.IRuntimeService
import tencent.im.oidb.cmd0x8fc.Oidb_0x8fc
import tencent.im.oidb.oidb_sso

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

        val reqBody = Oidb_0x8fc.ReqBody().apply {
            this.uint64_group_code.set(groupId.toLong())
            this.rpt_mem_level_info.add(
                Oidb_0x8fc.MemberInfo().apply {
                    this.uint64_uin.set(uin.toLong())
                    this.bytes_uin_name.set(ByteStringMicro.copyFromUtf8(troopMemberNickNoEmpty))
                    this.bytes_special_title.set(ByteStringMicro.copyFromUtf8(title))
                    this.uint32_special_title_expire_time.set(-1) // 过期时间 -1 表示永久有效
                }
            )
        }
        val oIDBSSOPkg = oidb_sso.OIDBSSOPkg().apply {
            this.uint32_command.set(2300)
            this.uint32_service_type.set(2)
            this.bytes_bodybuffer.set(ByteStringMicro.copyFrom(reqBody.toByteArray()))
        }
        val newIntent = NewIntent(
            BaseApplicationImpl.sApplication,
            ProtoServlet::class.java
        ).apply {
            this.putExtra("cmd", "OidbSvc.0x8fc_2")
            this.putExtra("data", oIDBSSOPkg.toByteArray())
            this.setObserver { _, isSuccess, extras ->
                val sucMsg = "设置头衔成功"
                val failMsg = "设置头衔失败"
                if (isSuccess && extras != null && extras.getByteArray("data") != null) {
                    Toasts.success(sucMsg)
                } else {
                    Toasts.error(failMsg)
                }
            }
        }

        QQInterfaces.appRuntime.startServlet(newIntent)
    }

    fun getGroupInfo(groupId: String): TroopInfo {
        return runtime<ITroopInfoService>().getTroopInfo(groupId)
    }

    fun getUidFromUin(uin: String): String {
        return api<IRelationNTUinAndUidApi>().getUinFromUid(uin)
    }

    fun getUinFromUid(uid: String): String {
        return api<IRelationNTUinAndUidApi>().getUidFromUin(uid)
    }

    private inline fun <reified T : QRouteApi> api(): T {
        return QRoute.api(T::class.java)
    }

    private inline fun <reified T : IRuntimeService> runtime(): T {
        return QQInterfaces.appRuntime.getRuntimeService(T::class.java, "all")
    }
}
