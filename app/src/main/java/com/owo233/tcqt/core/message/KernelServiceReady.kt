package com.owo233.tcqt.core.message

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.log.LogUtils
import com.tencent.qqnt.kernel.api.IKernelService
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内核服务就绪后的挂载点 —— 模块里**唯一** hook `KernelServiceImpl.initService` 的地方。
 *
 * `initService` 在登录 / 重登时会被多次调用，因此这里把服务实例广播给订阅者，
 * 而不是让每个功能各自再 hook 一遍同一个方法。
 *
 * 订阅分两类，按能力划分、彼此不耦合：
 * - [MessageCore] 由本类直接挂载：消息流（服务端推送 / 本机发送）；
 * - [always] 每次就绪都通知（返回值会变的场景）；
 * - [once] 只在首个实例就绪时通知一次（装 hook 用）。
 */
object KernelServiceReady {

    private const val KERNEL_SERVICE_IMPL = "com.tencent.qqnt.kernel.api.impl.KernelServiceImpl"

    private val onceHandlers = CopyOnWriteArrayList<(IKernelService) -> Unit>()

    private val repeatHandlers = CopyOnWriteArrayList<(IKernelService) -> Unit>()

    @Volatile
    private var lastService: IKernelService? = null

    @Volatile
    private var hookInstalled = false

    /** 安装 `initService` 钩子；幂等。 */
    fun installHook() {
        if (hookInstalled) return

        synchronized(this) {
            if (hookInstalled) return

            runCatching {
                HookEnv.hostClassLoader.loadClass(KERNEL_SERVICE_IMPL)
                    .hookMethodAfter("initService") { param ->
                        val service = param.thisObject as? IKernelService
                            ?: return@hookMethodAfter
                        onReady(service)
                    }
                hookInstalled = true
                LogUtils.androidNoFilter.i("KernelServiceReady: 已挂载 $KERNEL_SERVICE_IMPL#initService")
            }.onFailure {
                LogUtils.androidNoFilter.e("KernelServiceReady: 挂载 initService 失败", it)
            }
        }
    }

    /** 只在首个内核实例就绪时执行一次；内核已就绪则立即补执行。 */
    fun once(tag: String, block: (IKernelService) -> Unit) {
        synchronized(onceHandlers) {
            lastService?.let { service ->
                runCatching { block(service) }
                    .onFailure { LogUtils.androidNoFilter.e("KernelServiceReady: $tag 补执行失败", it) }
                return
            }
            onceHandlers += block
        }
    }

    /** 每次内核就绪都执行。 */
    fun always(block: (IKernelService) -> Unit) {
        repeatHandlers += block
    }

    private fun onReady(service: IKernelService) {
        lastService = service

        runCatching { MessageCore.attach(service) }
            .onFailure { LogUtils.androidNoFilter.e("KernelServiceReady: 消息分发器挂载失败", it) }

        repeatHandlers.forEach { handler ->
            runCatching { handler(service) }
                .onFailure { LogUtils.androidNoFilter.e("KernelServiceReady: 订阅者处理失败", it) }
        }

        synchronized(onceHandlers) {
            onceHandlers.toList().forEach { handler ->
                if (onceHandlers.remove(handler)) {
                    runCatching { handler(service) }
                        .onFailure { LogUtils.androidNoFilter.e("KernelServiceReady: 一次性订阅者处理失败", it) }
                }
            }
        }
    }
}
