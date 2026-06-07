package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import mqq.app.MobileQQ

@RegisterAction
class MsgAntiRecall : IAction {

    override val name: String get() = "消息防撤回"
    override val desc: String get() = "防止消息被撤回，添加灰条提示。"
    override val uiTab: String get() = "基础"
    override val uiOrder: Int get() = 1
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                "msg_anti_recall.type",
                "选择解析方式",
                0,
                "",
                listOf("使用新版解析方式")
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) = Unit

    override val key: String get() = "msg_anti_recall"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean {
        KernelServiceImpl::class.java.hookMethodAfter("initService") {
            // 登录后触发Hook2次，退出登录后触发Hook1次，未登录状态打开QQ不会触发Hook
            val service = it.thisObject as IKernelService
            NTServiceFetcher.onFetch(service)
        }

        runCatching {
            val runtime = MobileQQ.getMobileQQ().peekAppRuntime()
            if (runtime != null && runtime.isLogin) {
                val service = runtime.getRuntimeService(IKernelService::class.java, "all")
                NTServiceFetcher.onFetch(service)
            }
        }

        return TCQTSetting.getBoolean(key)
    }
}
