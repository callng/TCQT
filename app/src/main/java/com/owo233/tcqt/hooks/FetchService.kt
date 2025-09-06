package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl

@RegisterAction
@RegisterSetting(
    key = "fetch_service",
    name = "消息防撤回",
    type = SettingType.BOOLEAN,
    desc = "防止消息被撤回，添加灰条提示。",
    uiOrder = 11
)
class FetchService: AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        KernelServiceImpl::class.java.hookMethod("initService").after {
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
