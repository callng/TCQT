package com.owo233.tcqt.features.menu

import android.app.Activity
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.hook.Chain
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.getObjectByType
import com.owo233.tcqt.features.menu.troop.TroopManagementContent
import com.owo233.tcqt.features.menu.troop.TroopManagementDialog
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.api.GroupService
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
object SimpleTroopManagement : Feature(
    key = "simple_troop_management",
    name = "简易群管菜单",
    desc = "点击群成员头像开启群管菜单,快速进行群成员权限相关操作。",
), DexKitTask {


    override fun install() {
        requireClass("onClick").getMethod(
            "onClick",
            View::class.java
        ).hookReplace { param ->
            handleClick(param)
        }
    }

    private fun handleClick(param: Chain): Any? {
        val view = param.args[0] as View
        val component = param.thisObject.getObjectByType<AIOAvatarContentComponent>()
        val msgItem = component.getObjectByType<AIOMsgItem>()
        val msgRecord = msgItem.msgRecord

        if (msgRecord.chatType != 2) return param.invokeOriginal()

        val groupId = msgRecord.peerUin.toString()
        if (!GroupService.getGroupInfo(groupId).isOwnerOrAdmin) {
            return param.invokeOriginal()
        }

        val activity = view.context as? Activity ?: return param.invokeOriginal()

        showManagementSheet(
            activity,
            msgRecord,
            param
        )

        return null
    }

    private fun showManagementSheet(
        activity: Activity,
        msgRecord: MsgRecord,
        param: Chain,
    ) {
        val troopUin = msgRecord.peerUin.toString()
        val memberUin = msgRecord.senderUin.toString()
        val memberUid = msgRecord.senderUid.toString()
        val nick = msgRecord.sendMemberName.ifEmpty { msgRecord.sendRemarkName }
            .ifEmpty { msgRecord.sendNickName }

        fun dismissAndRun(dismiss: () -> Unit, action: () -> Unit) {
            dismiss()
            runAction(action)
        }

        TroopManagementDialog(activity) { dismiss ->
            TroopManagementContent(
                groupId = troopUin,
                memberUin = memberUin,
                memberNick = nick,
                memberUid = memberUid,
                onEnterProfile = {
                    dismissAndRun(dismiss) {
                        param.invokeOriginal()
                    }
                },
                onNoPermission = {
                    dismissAndRun(dismiss) {
                        param.invokeOriginal()
                    }
                },
                onRecall = {
                    dismissAndRun(dismiss) {
                        val contact = Contact(msgRecord.chatType, msgRecord.peerUid, msgRecord.guildId)
                        QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgRecord.msgId)) { errCode, errMsg ->
                            val sucMsg = "已撤回该消息"
                            val failMsg = "撤回消息失败"
                            if (errCode != 0) {
                                Toasts.error("$failMsg, $errMsg ($errCode)")
                            } else {
                                Toasts.success(sucMsg)
                            }
                        }
                    }
                },
                onSetAdmin = {
                    dismissAndRun(dismiss) {
                        GroupService.modifyMemberRole(troopUin, memberUin, true)
                    }
                },
                onCancelAdmin = {
                    dismissAndRun(dismiss) {
                        GroupService.modifyMemberRole(troopUin, memberUin, false)
                    }
                },
                onSetMute = { duration ->
                    GroupService.setMemberShutUp(troopUin, memberUin, duration)
                },
                onCancelMute = {
                    dismissAndRun(dismiss) {
                        GroupService.setMemberShutUp(troopUin, memberUin, 0)
                    }
                },
                onSetTitle = { title ->
                    GroupService.setMemberTitle(troopUin, memberUin, title)
                },
                onSetCard = { card ->
                    GroupService.modifyMemberCardName(troopUin, memberUin, card)
                    msgRecord.sendMemberName = card
                },
                onKick = {
                    GroupService.kickMember(troopUin, memberUin, false)
                },
                onKickBlock = {
                    GroupService.kickMember(troopUin, memberUin, true)
                },
                onMuteAll = {
                    dismissAndRun(dismiss) {
                        GroupService.setGroupShutUp(troopUin, true)
                    }
                },
                onUnmuteAll = {
                    dismissAndRun(dismiss) {
                        GroupService.setGroupShutUp(troopUin, false)
                    }
                },
                getCurrentCard = { nick },
                onDismiss = dismiss
            )
        }.show()
    }

    private fun runAction(action: () -> Unit) {
        runCatching {
            action()
        }.onFailure {
            Log.e("SimpleTroopManagement runAction failed", it)
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "onClick" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.avatar")
            matcher {
                addInterface(View.OnClickListener::class.java.name)
                methods {
                    add { name("onClick") }
                }
            }
        }
    )
}
