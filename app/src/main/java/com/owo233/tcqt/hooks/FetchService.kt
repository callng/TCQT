package com.owo233.tcqt.hooks

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.logI
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import mqq.app.MobileQQ

class FetchService: IAction {

    override fun onRun(ctx: Context) {
        // killMsfService()
        KernelServiceImpl::class.java.hookMethod("initService").after {
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }
    }

    @Suppress("DEPRECATION")
    private fun getMsfServiceInfo(): ActivityManager.RunningServiceInfo? {
        val context = MobileQQ.getContext()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = am.getRunningServices(Int.MAX_VALUE)

        return services.firstOrNull { service ->
            service.service.className == MSF_SERVICE_NAME &&
                    service.service.packageName == context.packageName
        }
    }

    private fun killMsfService() {
        val info = getMsfServiceInfo() ?: return
        logI(msg = "kill msf service pid = ${info.pid}")
        Process.killProcess(info.pid)
    }

    override val name: String get() = "消息防撤回"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    companion object {
        private const val MSF_SERVICE_NAME = "com.tencent.mobileqq.msf.service.MsfService"
    }
}
