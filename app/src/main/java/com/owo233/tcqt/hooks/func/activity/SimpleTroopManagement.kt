package com.owo233.tcqt.hooks.func.activity

import android.app.Activity
import android.app.Application
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.ui.troopmanagement.TroopManagementContent
import com.owo233.tcqt.ui.troopmanagement.TroopManagementDialog
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.utils.api.GroupService
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.getObjectByType
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class SimpleTroopManagement : IAction, DexKitTask {

    override val key: String get() = "simple_troop_management"
    override val name: String get() = "简易群管菜单"
    override val desc: String get() = "点击群聊对于群成员头像开启群管菜单，快速进行群成员相关操作。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
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
