package com.owo233.tcqt.core.group

import com.owo233.tcqt.core.env.runOnce
import com.owo233.tcqt.core.group.GroupEventCore.uidToUinResolver
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.proto.asLong
import com.owo233.tcqt.core.proto.asMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 群事件的宿主入口安装点（core 层，只负责挂 hook 与派发）。
 *
 * 三个入口都在真机核对过（QQ 9.3.70）：
 *
 * | 事件 | 宿主入口 |
 * | :--- | :--- |
 * | 入群 | `com.tencent.qqnt.push.processor.TroopMemberAddPushProcessor#a(ArrayList)` |
 * | 退群 | `com.tencent.qq.mobileqq.troop.api.impl.TroopMemberInfoServiceImpl#deleteTroopMember(String, String, Z)` |
 * | 禁言 | 内核 `onMsfPush`（`OlPushService.MsgPush`，消息头 732/12） |
 *
 * UID → UIN 需要宿主关系接口，core 层不能直接依赖，因此由上层注入
 * [uidToUinResolver]（与 `DynamicActivityPort` 同一模式）。
 */
object GroupEventCore {

    private const val JOIN_PROCESSOR_CLASS = "com.tencent.qqnt.push.processor.TroopMemberAddPushProcessor"
    private const val TROOP_MEMBER_SERVICE_CLASS =
        "com.tencent.mobileqq.troop.api.impl.TroopMemberInfoServiceImpl"

    private val installed = AtomicBoolean(false)

    /**
     * UID → UIN 解析器。
     *
     * 未注入时事件仍会派发，但 `memberUin` 为空串（脚本侧判空即可）。
     */
    @Volatile
    var uidToUinResolver: ((String) -> String)? = null

    /** @return 是否注入成功；[resolver] 抛异常时按空串处理。 */
    fun resolveUin(uid: String): String {
        if (uid.isEmpty()) return ""
        return runCatching { uidToUinResolver?.invoke(uid).orEmpty() }.getOrDefault("")
    }

    /**
     * 安装三个入口。可重复调用，只生效一次。
     *
     * @param hostClassLoader 宿主类加载器
     */
    fun install(hostClassLoader: ClassLoader) {
        installed.runOnce {
            installJoinHook(hostClassLoader)
            installQuitHook(hostClassLoader)
        }
    }

    /** 入群：`TroopMemberAddPushProcessor#a(ArrayList)`。 */
    private fun installJoinHook(hostClassLoader: ClassLoader) {
        runCatching {
            val clazz = hostClassLoader.loadClass(JOIN_PROCESSOR_CLASS)
            clazz.hookMethodAfter("a", java.util.ArrayList::class.java) { param ->
                runCatching {
                    val bytes = (param.args[0] as? ArrayList<*>)?.toByteArray() ?: return@runCatching
                    val join = GroupEventParser.parseMemberAdd(bytes) ?: return@runCatching

                    GroupEventDispatcher.dispatch(
                        join.copy(memberUin = resolveUin(join.memberUid))
                    )
                }.onFailure { LogUtils.androidNoFilter.w("群事件: 入群处理失败", it) }
            }
            LogUtils.androidNoFilter.i("群事件: 已挂载入群入口 $JOIN_PROCESSOR_CLASS#a")
        }.onFailure { LogUtils.androidNoFilter.w("群事件: 挂载入群入口失败", it) }
    }

    /** 退群：`TroopMemberInfoServiceImpl#deleteTroopMember(String, String, Z)`。 */
    private fun installQuitHook(hostClassLoader: ClassLoader) {
        runCatching {
            val clazz = hostClassLoader.loadClass(TROOP_MEMBER_SERVICE_CLASS)
            clazz.hookMethodAfter(
                "deleteTroopMember",
                String::class.java,
                String::class.java,
                java.lang.Boolean.TYPE,
            ) { param ->
                runCatching {
                    val groupUin = param.args[0] as? String ?: return@runCatching
                    val memberUin = param.args[1] as? String ?: return@runCatching
                    LogUtils.androidNoFilter.i(
                        "群事件: deleteTroopMember 原始调用 group=$groupUin member=$memberUin"
                    )
                    if (groupUin.isEmpty()) return@runCatching

                    GroupEventDispatcher.dispatch(GroupEvent.MemberQuit(groupUin, memberUin))
                }.onFailure { LogUtils.androidNoFilter.w("群事件: 退群处理失败", it) }
            }
            LogUtils.androidNoFilter.i("群事件: 已挂载退群入口 $TROOP_MEMBER_SERVICE_CLASS#deleteTroopMember")
        }.onFailure { LogUtils.androidNoFilter.w("群事件: 挂载退群入口失败", it) }
    }

    /**
     * 本机 QQ 号提供者。
     *
     * 拍一拍要按「拍我」过滤，而 core 取不到宿主账号信息，因此由上层注入。
     * 未注入时不做过滤（事件照常派发）。
     */
    @Volatile
    var selfUinProvider: (() -> String)? = null

    /**
     * 内核推送：禁言 / 拍一拍事件。
     *
     * 由消息桥在 `onMsfPush` 上转发进来（与防撤回共用同一条推送通道，各自解析）。
     */
    fun onMsfPush(command: String, payload: ByteArray) {
        val selfUin = runCatching { selfUinProvider?.invoke().orEmpty() }.getOrDefault("")

        // 临时诊断：记录所有候选推送（拍一拍 732/20、528/290；禁言 732/12），
        // 用于确认推送是否到达 —— 解析失败时也要能看到"收到过"。
        runCatching { logCandidate(command, payload) }

        when (val event = GroupEventParser.parsePush(command, payload, selfUin)) {
            is GroupEvent.ShutUp ->
                GroupEventDispatcher.dispatch(event.copy(memberUin = resolveUin(event.memberUid)))

            is GroupEvent.PaiYiPai -> GroupEventDispatcher.dispatch(event)

            else -> Unit
        }
    }

    /** 临时诊断：打印候选推送的头与结构（732/528 段全记，避免漏未知子类型）。 */
    private fun logCandidate(command: String, payload: ByteArray) {
        if (!command.contains("OlPushService.MsgPush")) return

        val root = runCatching {
            GroupEventParser.decodeForDiag(payload)
        }.getOrNull() ?: run {
            LogUtils.androidNoFilter.w("推送诊断: 解码失败 len=${payload.size}")
            return
        }

        val head = runCatching { root.getOrNull(1, 2)?.asMap }.getOrNull()
        val t1 = head?.let { runCatching { it.getOrNull(1)?.asLong }.getOrNull() } ?: return
        val t2 = head.let { runCatching { it.getOrNull(2)?.asLong }.getOrNull() } ?: return

        // 只关心 732 / 528 段（消息头族），其它推送太杂
        if (t1 != 732L && t1 != 528L) return

        val json = runCatching { root.toJson().toString() }.getOrDefault("")
        LogUtils.androidNoFilter.i(
            "推送诊断: head=$t1/$t2 len=${payload.size} json=${json.take(1200)}"
        )
    }

    /** `ArrayList<Byte>` → `ByteArray`；元素类型不对时返回 null。 */
    private fun ArrayList<*>.toByteArray(): ByteArray? {
        val out = ByteArray(size)
        forEachIndexed { index, value ->
            val byte = (value as? Byte) ?: return null
            out[index] = byte
        }
        return out
    }
}
