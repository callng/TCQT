package com.owo233.tcqt.core.message

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.log.LogUtils
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

/**
 * 运行期定位真正的消息入口类。
 *
 * 服务端推送最终走到内核的 `IKernelMsgListener` 适配器上，**不是** `IKernelMsgService`：
 * 后者的 `onRecvMsg` 并不存在，宿主的消息回调方法只有这两个：
 *
 * ```
 * onRecvMsg(ArrayList<MsgRecord>)      // 服务端推送落地的消息
 * onAddSendMsg(MsgRecord)              // 本机发出的消息
 * ```
 *
 * 承载它们的类被混淆（QQ 9.3.70 上是 `com.tencent.qqnt.msg.j`，TAG `IMsgListenerAdapter`），
 * 因此只能用 DexKit 按**结构**找。
 *
 * 查表条件刻意收窄成"同一类里同时具备这两个方法"：只按 `onRecvMsg` 单方法匹配会命中
 * 一堆恰好实现同名回调的类（例如 `GameMsgManagerServiceImpl$c`），那些类收不到全量消息。
 */
internal object DexKitMessageService {

    private const val TARGET_PACKAGE = "com.tencent.qqnt.msg"
    private const val EXCLUDE_PACKAGE = "com.tencent.qqnt.msg.migration"

    private const val METHOD_RECEIVE = "onRecvMsg"
    private const val METHOD_SEND = "onAddSendMsg"

    /** native 转发类：回调不在它上面。 */
    private const val PROXY_MARKER = "CppProxy"

    @Volatile
    private var resolved: List<Class<*>>? = null

    private val lock = Any()

    /**
     * 幂等定位；命中一次后缓存，跨 `initService` 复用。
     *
     * 返回**列表**：真机上同一包里有基类与子类两个同形类
     * （QQ 9.3.70：`com.tencent.qqnt.msg.j` 与 `MsgService$d extends j`，后者重写了
     * `onRecvMsg` 并在其中 `super` 调用基类）。只取其中一个会在宿主改结构时静默失效，
     * 全部挂上更稳；同一条消息重复派发由 [MessageDedup] 兜住。
     */
    fun locate(): List<Class<*>> {
        resolved?.let { return it }

        return synchronized(lock) {
            resolved?.let { return it }

            val found = runCatching { query() }
                .onFailure { LogUtils.androidNoFilter.e("DexKitMessageService: 定位消息入口类失败", it) }
                .getOrDefault(emptyList())

            if (found.isNotEmpty()) {
                resolved = found
                LogUtils.androidNoFilter.i(
                    "DexKitMessageService: 消息入口类 = ${found.joinToString { it.name }}"
                )
            }
            found
        }
    }

    private fun query(): List<Class<*>> {
        // DexKit 是 native 实现：它的 so 只在主进程按需加载（DexKitFinder 里有
        // `ProcUtil.isMain` 判断），而消息入口在内核进程也要用，所以这里必须自己确保加载。
        // NativeLibs.load 内部有缓存，重复调用没有额外开销。
        if (!NativeLibs.load("dexkit")) {
            LogUtils.androidNoFilter.w("DexKitMessageService: libdexkit.so 未加载成功，无法定位消息入口类")
            return emptyList()
        }

        val loader = HookEnv.hostClassLoader

        val candidates = DexKitBridge.create(loader, true).use { bridge ->
            bridge.findClass(
                FindClass().apply {
                    searchPackages(TARGET_PACKAGE)
                    excludePackages(EXCLUDE_PACKAGE)
                    matcher {
                        methods {
                            add(MethodMatcher().apply { name(METHOD_RECEIVE) })
                            add(MethodMatcher().apply { name(METHOD_SEND) })
                        }
                    }
                }
            )
        }

        // 结构复核：必须同时具备两个方法，且参数形态正确。DexKit 只按名字筛，
        // 真正的签名在这里确认，避免把同名但参数不符的类当成入口。
        val matched = candidates
            .filterNot { it.name.contains(PROXY_MARKER) }
            .mapNotNull { load(it.name) }
            .filter(::isEntryShape)

        if (matched.isEmpty()) {
            LogUtils.androidNoFilter.w(
                "DexKitMessageService: 未命中消息入口类（候选 ${candidates.size} 个）"
            )
        }

        return matched
    }

    /** 入口类的形态：恰好一个 `ArrayList` 参数的 `onRecvMsg` + 恰好一个 `MsgRecord` 参数的 `onAddSendMsg`。 */
    private fun isEntryShape(clazz: Class<*>): Boolean {
        fun hasExact(name: String, param: Class<*>): Boolean =
            clazz.declaredMethods.any {
                it.name == name && it.parameterCount == 1 && it.parameterTypes[0] == param
            }

        return hasExact(METHOD_RECEIVE, ArrayList::class.java) &&
                hasExact(METHOD_SEND, MsgRecord::class.java)
    }

    private fun load(name: String): Class<*>? =
        runCatching { HookEnv.hostClassLoader.loadClass(name) }
            .onFailure { LogUtils.androidNoFilter.w("DexKitMessageService: 加载 $name 失败", it) }
            .getOrNull()

    /** 供 hook 前复核：取指定名字的单参数方法。 */
    fun findHookMethod(clazz: Class<*>, name: String): Method? =
        clazz.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 1 }
            ?.apply { isAccessible = true }
}