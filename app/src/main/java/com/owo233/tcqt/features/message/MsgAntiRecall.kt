package com.owo233.tcqt.features.message

import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.features.internal.pipeline.OnAIOViewUpdate
import com.owo233.tcqt.host.service.AntiRecallConfig
import com.owo233.tcqt.host.service.NTServiceFetcher
import com.owo233.tcqt.host.service.RecallTargetResolver
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.impl.KernelServiceImpl
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import mqq.app.MobileQQ

@RegisterAction
object MsgAntiRecall : Feature(
    key = "msg_anti_recall",
    name = "消息防撤回",
    desc = "阻止消息被撤回后删除，需要保活进程。",
    uiOrder = 1,
    processes = setOf(ActionProcess.MAIN),
), OnAIOViewUpdate {

    override val decoratorOrder: Int = 50

    override fun onGetViewNt(view: ViewGroup, msgRecord: MsgRecord, param: MethodHookParam) {
        if (canRun() && AntiRecallConfig.isGrayTipEnabled()) RecallTargetResolver.remember(msgRecord)
    }

    /**
     * settingKey 必须与 `AntiRecallConfig.SETTING_KEY` 一致；本功能只负责把该项注册进
     * 设置界面，读写与旧 key 迁移都由 `AntiRecallConfig` 承担，因此这里没有读取点。
     */
    @Suppress("unused")
    private val options by multiIntOption(
        settingKey = "type",
        name = "防撤回选项",
        defaultValue = AntiRecallConfig.DEFAULT_OPTIONS,
        options = listOf("使用新版解析方式", "底部灰字提醒", "顶部撤回提醒"),
    )

    override fun install() {
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
    }
}
