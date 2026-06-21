package com.owo233.tcqt.utils.api

import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.log.Log
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
            if (errCode != 0) {
                Log.e("setGroupShutUp: errCode: $errCode, errMsg: $errMsg")
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
            if (errCode != 0) {
                Log.e("setMemberShutUp: errCode: $errCode, errMsg: $errMsg")
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
            if (errCode != 0) {
                Log.e("modifyMemberRole: errCode: $errCode, errMsg: $errMsg")
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
            if (errCode != 0) {
                Log.e("kickMember: errCode: $errCode, errMsg: $errMsg")
            }
        }
    }

    fun modifyMemberCardName(groupId: String, uin: String, name: String) {
        service.modifyMemberCardName(
            groupId.toLong(),
            getUidFromUin(uin),
            name
        ) { errCode, errMsg ->
            if (errCode != 0) {
                Log.e("modifyMemberCardName: errCode: $errCode, errMsg: $errMsg")
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
                if (isSuccess && extras != null) {
                    Log.d("setMemberTitle data: ${extras.getByteArray("data").toHexString()}")
                } else {
                    Log.e("setMemberTitle: 设置群成员头衔失败")
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
