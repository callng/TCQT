package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl

@RegisterAction
class FetchService : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        KernelServiceImpl::class.java.hookMethod("initService").after {
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
