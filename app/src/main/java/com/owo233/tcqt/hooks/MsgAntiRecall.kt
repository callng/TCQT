package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.hookMethod
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import kotlinx.coroutines.DelicateCoroutinesApi

@RegisterAction
@RegisterSetting(
    key = "msg_anti_recall",
    name = "消息防撤回",
    type = SettingType.BOOLEAN,
    desc = "防止消息被撤回，添加灰条提示。",
    uiOrder = 11
)
class MsgAntiRecall : IAction {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onRun(ctx: Context, process: ActionProcess) {
        Log.d("Oh baby baby tell me why")
    }

    override val key: String get() = GeneratedSettingList.MSG_ANTI_RECALL

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean {
        KernelServiceImpl::class.java.hookMethod("initService", afterHook {
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        })

        return GeneratedSettingList.getBoolean(key)
    }
}
