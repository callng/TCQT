package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.PlatformTools
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl

class FetchService: IAction {

    override fun onRun(ctx: Context) {
        if (PlatformTools.isQQNt()) {
            KernelServiceImpl::class.java.hookMethod("initService").after {
                val service = it.thisObject as IKernelService
                NTServiceFetcher.onFetch(service)
            }
        }
    }

    override val name: String get() = "消息防撤回"

    override val process: ActionProcess = ActionProcess.MAIN
}
