package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.LogUtils
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo
import mqq.app.MobileQQ
import top.artmoe.inao.item.NewPreventRetractingMessageCore
import java.util.concurrent.ConcurrentHashMap

object NTServiceFetcher {

    private lateinit var iKernelService: IKernelService
    private val hookInstallations = HookInstallationRegistry()
    private val observedPushCommands = ConcurrentHashMap.newKeySet<String>()

    fun onFetch(service: IKernelService) {
        this.iKernelService = service // initService钩子会被多次调用，允许它重新赋值

        if (!TCQTSetting.getBoolean("msg_anti_recall")) return

        val sessionClass = try {
            service.wrapperSession.javaClass
        } catch (t: Throwable) {
            LogUtils.xposedNoFilter.e("AntiRecall wrapper session is not ready", t)
            return
        }

        msgPushHook(sessionClass)
    }

    private fun msgPushHook(sessionClass: Class<*>) {
        installHook(sessionClass, HookInstallationRegistry.Hook.MSF_PUSH) {
            sessionClass.hookMethodBefore(
                "onMsfPush",
                String::class.java,
                ByteArray::class.java,
                PushExtraInfo::class.java
            ) { param ->
                val cmd = param.args.getOrNull(0) as? String ?: return@hookMethodBefore
                val buffer = param.args.getOrNull(1) as? ByteArray ?: return@hookMethodBefore
                if (isRecallPushCommand(cmd) && observedPushCommands.add(cmd)) {
                    LogUtils.xposedNoFilter.i("AntiRecall push callback active: $cmd")
                }
                action(cmd, buffer, param)
            }
        }

        installHook(sessionClass, HookInstallationRegistry.Hook.BACKGROUND) {
            sessionClass.hookMethodBefore("switchToBackGround") {
                if (TCQTSetting.getBoolean("msg_anti_recall")) it.result = null
            }
        }
    }

    private fun installHook(
        sessionClass: Class<*>,
        hook: HookInstallationRegistry.Hook,
        install: () -> Unit,
    ) {
        runCatching { hookInstallations.installIfMissing(sessionClass, hook, install) }
            .onSuccess { installed ->
                if (installed) {
                    LogUtils.xposedNoFilter.i("AntiRecall hook installed: $hook on ${sessionClass.name}")
                }
            }
            .onFailure {
                LogUtils.xposedNoFilter.e("AntiRecall hook installation failed: $hook on ${sessionClass.name}", it)
            }
    }

    private fun isRecallPushCommand(cmd: String): Boolean =
        cmd == "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" ||
                cmd == "trpc.msg.olpush.OlPushService.MsgPush"

    private fun action(cmd: String, buffer: ByteArray, param: MethodHookParam) {
        if (!TCQTSetting.getBoolean("msg_anti_recall")) return

        // 新旧解析器的区别：旧版用 Google Protobuf，新版用 kotlinx-serialization
        val handler: MessageHandler = if (AntiRecallConfig.useNewParser()) {
            NewPreventRetractingMessageCore
        } else {
            AioListener
        }

        when (cmd) {
            "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" ->
                handler.handleInfoSyncPush(buffer, param)
            "trpc.msg.olpush.OlPushService.MsgPush" ->
                handler.handleMsgPush(buffer, param)
        }
    }

    val kernelService: IKernelService
        get() {
            if (!::iKernelService.isInitialized) {
                runCatching {
                    val runtime = MobileQQ.getMobileQQ().peekAppRuntime()
                    if (runtime != null) {
                        val service = runtime.getRuntimeService(IKernelService::class.java, "all")
                        iKernelService = service
                    }
                }
            }
            return iKernelService
        }
}
