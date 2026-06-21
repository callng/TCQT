package com.owo233.tcqt.hooks.func.activity

import android.app.Activity
import android.app.Application
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.utils.api.GroupService
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.reflect.getObjectByType
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class SimpleTroopManagement : IAction, DexKitTask {

    override val key: String get() = "simple_troop_management"
    override val name: String get() = "简易群管"
    override val desc: String get() = "点击群聊头像开启菜单，免去多余步骤进入主页管理群员。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        requireClass("onClick").getMethod(
            "onClick",
            View::class.java
        ).hookReplace { param ->
            val thisObject = param.thisObject
            val view = param.args[0] as View

            val component = thisObject.getObjectByType<AIOAvatarContentComponent>()
            val msgItem = component.getObjectByType<AIOMsgItem>()
            val msgRecord = msgItem.msgRecord

            if (msgRecord.chatType != 2) return@hookReplace param.invokeOriginal()

            val groupId = msgRecord.peerUin.toString()
            val troopInfo = GroupService.getGroupInfo(groupId)
            if (!troopInfo.isOwnerOrAdmin) return@hookReplace param.invokeOriginal()

            showManagementSheet(view.context as Activity, msgRecord, param, troopInfo.isOwner)
            return@hookReplace null
        }
    }

    private fun showManagementSheet(
        activity: Activity,
        msgRecord: MsgRecord,
        param: Chain,
        isOwner: Boolean
    ) {
        Toasts.error("简易群管: 还没实现哦~")
        param.invokeOriginal()
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
