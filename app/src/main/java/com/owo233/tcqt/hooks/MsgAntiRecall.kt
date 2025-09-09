package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.ext.runRetry
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.AioListener
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.logD
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

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
        GlobalScope.launchWithCatch {
            runRetry(retryNum = 10, sleepMs = 233) {
                NTServiceFetcher.kernelService
            }!!.wrapperSession.javaClass.hookMethod("onMsfPush").before { param ->
                val cmd = param.args[0] as String
                val buffer = param.args[1] as ByteArray
                when(cmd) {
                    "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                        AioListener.handleInfoSyncPush(buffer, param)
                    }
                    "trpc.msg.olpush.OlPushService.MsgPush" -> {
                        AioListener.handleMsgPush(buffer, param)
                    }
                    else -> { }
                }
            }

            if (TCQTBuild.DEBUG) { // 仅供调试
                NTServiceFetcher.kernelService.wrapperSession.javaClass.hookMethod("setQimei36").before {
                    logD(msg = "setQimei36: ${it.args[0] as String}")
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.MSG_ANTI_RECALL

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean {
        KernelServiceImpl::class.java.hookMethod("initService").after {
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }

        val isEnabled = GeneratedSettingList.getBoolean(key)
        return isEnabled
    }
}
