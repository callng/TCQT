package com.owo233.tcqt.core.message

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.runOnce
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.LogUtils
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MSF 推送的集中转发点 —— 模块里**唯一** hook 内核 `onMsfPush` 的地方。
 *
 * 原来这条推送通道只服务防撤回（`NTServiceFetcher` 独占 hook）。群禁言等事件同样
 * 要从这里取数据，于是把它提出来做分发：谁需要谁订阅，互不干扰，也避免同一条
 * 方法被多个功能各自 hook 一遍。
 *
 * 与 [MessageCore] 的区别：那个解析的是**结构化消息**（`IKernelMsgListener`），
 * 这里转发的是**原始推送包**（cmd + 字节），由订阅者各按自己的协议解析。
 */
object MsfPushRouter {

    private const val KERNEL_SERVICE_IMPL = "com.tencent.qqnt.kernel.api.impl.KernelServiceImpl"

    fun interface Listener {
        /**
         * @param command 推送的 serviceCmd，例如 `trpc.msg.olpush.OlPushService.MsgPush`
         * @param payload 原始字节
         * @param param   原始 hook 参数；防撤回需要改写参数时用它
         */
        fun onPush(command: String, payload: ByteArray, param: com.owo233.tcqt.core.hook.MethodHookParam)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    private val installed = AtomicBoolean(false)

    /** 订阅推送；安装钩子。幂等。 */
    fun subscribe(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        installHook()
    }

    fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
    }

    /** 安装 `wrapperSession#onMsfPush` 钩子；幂等。 */
    private fun installHook() {
        installed.runOnce {
            KernelServiceReady.once("MsfPushRouter") { service -> attach(service) }
        }
    }

    /** 由 [KernelServiceReady] 在首个服务就绪时调用。 */
    internal fun attach(service: IKernelService) {
        runCatching {
            service.wrapperSession.javaClass.hookMethodBefore(
                "onMsfPush",
                String::class.java,
                ByteArray::class.java,
                PushExtraInfo::class.java,
            ) { param ->
                val command = param.args.getOrNull(0) as? String ?: return@hookMethodBefore
                val payload = param.args.getOrNull(1) as? ByteArray ?: return@hookMethodBefore

                listeners.forEach { listener ->
                    runCatching { listener.onPush(command, payload, param) }
                        .onFailure {
                            LogUtils.androidNoFilter.w("MSF 推送订阅者异常: $command", it)
                        }
                }
            }
            LogUtils.androidNoFilter.i("MsfPushRouter: 已挂载内核 onMsfPush")
        }.onFailure {
            LogUtils.androidNoFilter.e("MsfPushRouter: 挂载 onMsfPush 失败", it)
        }
    }

    /** 仅测试用。 */
    internal fun clearForTest() {
        listeners.clear()
    }

    internal val listenerCount: Int get() = listeners.size

    /** 供 [GroupEventCore] 这类 core 组件确认宿主类加载器可用。 */
    internal val hostClassLoader: ClassLoader get() = HookEnv.hostClassLoader
}
