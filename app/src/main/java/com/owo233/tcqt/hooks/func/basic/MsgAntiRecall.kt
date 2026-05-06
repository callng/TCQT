package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import kotlinx.coroutines.DelicateCoroutinesApi

@RegisterAction
@RegisterSetting(
    key = "msg_anti_recall",
    name = "消息防撤回",
    type = SettingType.BOOLEAN,
    desc = "防止消息被撤回，添加灰条提示。",
    uiOrder = 1,
    uiTab = "基础"
)
@RegisterSetting(
    key = "msg_anti_recall.type",
    name = "选择解析方式",
    type = SettingType.INT_MULTI,
    defaultValue = "0",
    options = "使用新版解析方式"
)
class MsgAntiRecall : IAction {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onRun(app: Application, process: ActionProcess) = Unit

    override val key: String get() = GeneratedSettingList.MSG_ANTI_RECALL

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean {
        KernelServiceImpl::class.java.hookMethodAfter("initService") {
            // 登录后触发Hook2次，退出登录后触发Hook1次，未登录状态打开QQ不会触发Hook
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }

        return GeneratedSettingList.getBoolean(key)
    }
}
