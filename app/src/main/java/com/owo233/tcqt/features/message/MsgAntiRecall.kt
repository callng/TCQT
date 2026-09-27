package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.message.KernelServiceReady
import com.owo233.tcqt.host.service.AntiRecallConfig
import com.owo233.tcqt.host.service.NTServiceFetcher
import com.tencent.qqnt.kernel.api.IKernelService
import mqq.app.MobileQQ

@RegisterAction
object MsgAntiRecall : Feature(
    key = "msg_anti_recall",
    name = "消息防撤回",
    desc = "阻止消息被撤回后删除，需要保活进程。",
    uiOrder = 1,
) {

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

        // initService 的 hook 已集中在 KernelMessageBridge（全进程装一次），
        // 防撤回只订阅内核就绪事件，不再自己 hook 同一个方法。
        KernelServiceReady.once("MsgAntiRecall") { service ->
            NTServiceFetcher.onFetch(service)
        }

        // 内核在本功能安装之前就已就绪时（模块后加载 / 热重载）补一次。
        runCatching {
            val runtime = MobileQQ.getMobileQQ().peekAppRuntime()
            if (runtime != null && runtime.isLogin) {
                val service = runtime.getRuntimeService(IKernelService::class.java, "all")
                NTServiceFetcher.onFetch(service)
            }
        }
    }
}
