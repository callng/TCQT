package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.hooks.helper.AntiRecallConfig
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import mqq.app.MobileQQ

@RegisterAction
class MsgAntiRecall : IAction {

    override val key: String get() = "msg_anti_recall"
    override val name: String get() = "消息防撤回"
    override val desc: String get() = "阻止消息被撤回后删除，需要保活进程。"
    override val uiTab: String get() = "基础"
    override val uiOrder: Int get() = 1
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                AntiRecallConfig.SETTING_KEY,
                "防撤回选项",
                AntiRecallConfig.DEFAULT_OPTIONS,
                "",
                listOf("使用新版解析方式", "底部灰字提醒", "顶部撤回提醒")
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) = Unit

    override fun onInit(): Boolean {
        AntiRecallConfig.migrateLegacyOptions()

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

        return super.onInit()
    }
}
