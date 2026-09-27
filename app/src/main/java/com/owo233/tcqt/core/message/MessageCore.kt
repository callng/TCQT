package com.owo233.tcqt.core.message

import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.LogUtils
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 服务端消息的分发中心 —— 模块里**唯一**把"QQ 收到了消息"变成事件的地方。
 *
 * 数据源是内核接收推送的回调（`onAddSendMsg` / `onRecvMsg`），不是视图管线：
 * 视图只在用户真的看到那条消息时才更新，拿它当消息源会漏掉后台消息、历史漫游、
 * 撤回等场景，也无法"实时推送"。
 *
 * 入口类由 [DexKitMessageService] 定位（宿主里是混淆名，QQ 9.3.70 上为
 * `com.tencent.qqnt.msg.j`，TAG `IMsgListenerAdapter`）；本类只负责
 * 「挂 hook + 去重 + 广播」，不解释消息内容。
 */
internal object MessageCore {

    private const val METHOD_SEND = "onAddSendMsg"
    private const val METHOD_RECEIVE = "onRecvMsg"

    private val sources = listOf(
        HookedSource(MessageKind.SEND, METHOD_SEND),
        HookedSource(MessageKind.RECEIVE, METHOD_RECEIVE),
    )

    @Volatile
    var isInstalled: Boolean = false
        private set

    /**
     * 幂等安装。宿主 `initService` 会被多次触发，允许重复调用；
     * 个别入口当次没解析出来（DexKit 未命中）时，下次调用会重试。
     */
    fun attach(service: IKernelService) {
        val entries = MessageServiceLocator.locate(service)

        if (entries.isEmpty()) {
            LogUtils.androidNoFilter.w("MessageCore: 未定位到消息入口类，本次不挂载")
            return
        }

        entries.forEach { clazz ->
            sources.forEach { source ->
                if (source.hooks.any { it.declaringClass == clazz }) return@forEach
                runCatching { attachSource(source, clazz) }
                    .onFailure { LogUtils.androidNoFilter.e("MessageCore: 挂载 ${source.name} 失败", it) }
            }
        }

        isInstalled = sources.all { it.hooks.isNotEmpty() }
    }

    private fun attachSource(source: HookedSource, clazz: Class<*>) {
        val method = clazz.hookCandidate(source.methodName) ?: run {
            LogUtils.androidNoFilter.w("MessageCore: ${clazz.name} 里没有 ${source.methodName}")
            return
        }

        method.hookAfter { param -> dispatch(source, param) }
        source.hooks += method

        LogUtils.androidNoFilter.i("MessageCore: 已挂载 ${source.name} -> ${clazz.name}#${method.name}")
    }

    private fun dispatch(source: HookedSource, param: MethodHookParam) {
        // onRecvMsg(ArrayList<MsgRecord>) 与 onAddSendMsg(MsgRecord) 参数形态不同，都接受。
        when (val arg = param.args.firstOrNull()) {
            is Collection<*> -> arg.forEach { record ->
                (record as? MsgRecord)?.let { publish(source, it) }
            }

            is MsgRecord -> publish(source, arg)
        }
    }

    // ── 事件发布 ─────────────────────────────────────────────────────────

    private val dedup = MessageDedup()

    private val loggedSources = CopyOnWriteArrayList<String>()

    private fun publish(source: HookedSource, record: MsgRecord) {
        // 同一条推送可能同时命中两个入口（onRecvMsg 与 onAddSendMsg、多内核实例）。
        // 没有 msgId 的消息无法去重，只能原样放行。
        if (record.msgId != 0L && !dedup.tryMark(record.msgId)) return

        val event = runCatching { MessageEvent(source.kind, record) }
            .onFailure { LogUtils.androidNoFilter.e("MessageCore: 构造消息事件失败", it) }
            .getOrNull() ?: return

        // 每条入口的首次真实命中打一条日志：用来区分「hook 没挂上」与「挂上了但没消息」。
        if (loggedSources.add(source.name)) {
            LogUtils.androidNoFilter.i("MessageCore: ${source.name} 首次收到消息 -> $event")
        }

        MessageDispatcher.dispatch(event)
    }

    /**
     * 找可 hook 的单参数方法。
     *
     * 两个入口参数形态不同：`onRecvMsg(ArrayList<MsgRecord>)` 与
     * `onAddSendMsg(MsgRecord)`，两种都接受，但必须恰好一个参数且形参就是这两者之一，
     * 避免误 hook 同名的不相干方法。
     */
    private fun Class<*>.hookCandidate(name: String): Method? =
        declaredMethods.firstOrNull { method ->
            if (method.name != name || method.parameterCount != 1) return@firstOrNull false

            val type = method.parameterTypes[0]
            type == ArrayList::class.java || MsgRecord::class.java.isAssignableFrom(type)
        }?.apply { isAccessible = true }

    private class HookedSource(
        val kind: MessageKind,
        val methodName: String,
    ) {

        val name: String get() = "${kind.name.lowercase()}#$methodName"

        val hooks = CopyOnWriteArrayList<Method>()
    }
}

/** 已注册监听器表；[MessageCore] 只向这里广播。 */
internal object MessageDispatcher {

    private val listeners = CopyOnWriteArrayList<MessageListener>()

    fun register(listener: MessageListener) {
        if (listener !in listeners) listeners += listener
    }

    fun unregister(listener: MessageListener) {
        listeners -= listener
    }

    /**
     * 清空监听器表；仅供测试隔离使用。
     *
     * 生产路径不需要它：监听器随进程存活，脚本运行态由 `ScriptRegistry` 管理。
     */
    internal fun clearForTest() {
        listeners.clear()
    }

    fun dispatch(event: MessageEvent) {
        listeners.forEach { listener ->
            try {
                listener.onMessage(event)
            } catch (t: Throwable) {
                // 日志本身也必须兜住：否则"某一个监听器抛异常"会连带把广播打断，
                // 后面的监听器一条消息都收不到。
                runCatching { LogUtils.androidNoFilter.e("MessageCore: 监听器处理消息失败", t) }
            }
        }
    }
}

/**
 * 消息入口类候选。
 *
 * 主路径是 DexKit 结构定位（见 [DexKitMessageService]）；另外把宿主给出的运行时类
 * 也纳入候选：某些版本的消息适配器可能直接由 `wrapperSession` 持有且未被混淆，
 * 那就没必要走 DexKit。两处候选都做形态复核，避免挂错类。
 */
internal object MessageServiceLocator {

    private const val PROXY_MARKER = "CppProxy"

    private const val METHOD_RECEIVE = "onRecvMsg"
    private const val METHOD_SEND = "onAddSendMsg"

    fun locate(service: IKernelService): List<Class<*>> {
        val result = linkedSetOf<Class<*>>()

        result += DexKitMessageService.locate()

        runCatching { service.wrapperSession.msgService.javaClass }
            .getOrNull()
            ?.takeIf { !it.name.contains(PROXY_MARKER) && hasEntryShape(it) }
            ?.let(result::add)

        return result.toList()
    }

    private fun hasEntryShape(clazz: Class<*>): Boolean {
        fun has(name: String): Boolean =
            clazz.declaredMethods.any { it.name == name && it.parameterCount == 1 }

        return has(METHOD_RECEIVE) && has(METHOD_SEND)
    }
}