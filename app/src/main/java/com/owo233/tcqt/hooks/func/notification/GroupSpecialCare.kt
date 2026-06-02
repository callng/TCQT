package com.owo233.tcqt.hooks.func.notification

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.LogUtils
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod

@RegisterAction
class GroupSpecialCare : IAction, DexKitTask {

    override val name: String get() = "关闭群普通消息特别关心提示"
    override val desc: String get() = "仅在特别关心发送群消息时提示，阻止群内存在特别关心消息时其他成员普通消息使用特别关心提示。（9.2.95+或许不工作）"
    override val uiTab: String get() = "通知"
    override val key: String get() = "group_special_care_fix"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)

    override fun onRun(app: Application, process: ActionProcess) {
        val target = runCatching { requireMethod(TARGET) }
            .onFailure {
                LogUtils.xposedNoFilter.w("GroupSpecialCare hook target not found, feature skipped")
            }
            .getOrNull() ?: return

        target.hookAfter { param ->
            val map = param.thisObject.mutableMapField("h") ?: return@hookAfter
            val friendUin = param.args.getOrNull(1)?.stringField("frienduin") ?: return@hookAfter
            @Suppress("UNCHECKED_CAST")
            (map as MutableMap<Any?, Any?>).remove(friendUin)
        }
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        val result = targetQueries()
            .asSequence()
            .mapNotNull { query -> bridge.findMethod(query).firstOrNull() }
            .firstOrNull()
        if (result != null) {
            cache[TARGET] = result.descriptor
        }
    }

    private fun targetQueries(): List<FindMethod> = listOf(
        findNotifyIdManagerMethod("getCareTroopMemberMsgText: invoked.  troopMemberIsCared: "),
        findNotifyIdManagerMethod("SpecialCareGrayTipsHelper"),
        findNotifyIdManagerMethod("hasSpecialCareFriend"),
        findNotifyIdManagerMethod("handleGetCareBarEnable")
    )

    private fun findNotifyIdManagerMethod(trait: String): FindMethod {
        return FindMethod().apply {
            matcher {
                declaredClass("com.tencent.util.notification.NotifyIdManager")
                usingStrings(trait)
            }
        }
    }

    companion object {
        private const val TARGET = "GroupSpecialCare.getCareTroopMemberMsgText"
    }
}
