package com.owo233.tcqt.core.group

import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.proto.ProtoDecodeMode
import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.ProtoUtils
import com.owo233.tcqt.core.proto.ProtoValue
import com.owo233.tcqt.core.proto.asList
import com.owo233.tcqt.core.proto.asMap
import com.owo233.tcqt.core.proto.asString

/**
 * 图片 rkey 采集。
 *
 * rkey 是宿主向服务端换取、拼在图片地址后的签名参数，**没有公开的一次性获取接口**，
 * 只能从宿主实际收到的响应里采集。
 *
 * 抓取点：`OidbSvcTrpcTcp.0x9067_202` 的响应，结构为
 * `4.4.1` 一个数组，第 0 项是好友 rkey、第 1 项是群 rkey，值在各自 `1` 字段。
 *
 * 解析是纯函数（[parse]），可单测；抓取由 [install] 通过纯反射挂到宿主的响应分发上。
 */
object RKeyCollector {

    /** rkey 响应对应的 serviceCmd。 */
    const val RKEY_CMD = "OidbSvcTrpcTcp.0x9067_202"

    private const val RESP_HANDLER_CLASS = "mqq.app.msghandle.MsgRespHandler"

    @Volatile
    private var installed = false

    @Volatile
    var friendRkey: String = ""
        private set

    @Volatile
    var groupRkey: String = ""
        private set

    /**
     * 解析 rkey 响应。
     *
     * @return `(好友 rkey, 群 rkey)`；结构对不上或字节非法时返回 null
     */
    fun parse(payload: ByteArray): Pair<String, String>? = runCatching {
        val root = ProtoUtils.decodeFromByteArray(payload, ProtoDecodeMode.COMPATIBLE)

        val array = root.getOrNull(4, 4, 1)?.asList ?: return null
        if (array.size() < 2) return null

        val friend = arrayItemRkey(array[0])
        val group = arrayItemRkey(array[1])
        if (friend.isEmpty() && group.isEmpty()) return null

        friend to group
    }.getOrNull()

    /** 单个元素形如 `{ 1: "<rkey>" }`。 */
    private fun arrayItemRkey(item: ProtoValue?): String =
        runCatching { item?.asMap?.getOrNull(1)?.asString?.toStringUtf8().orEmpty() }
            .getOrDefault("")

    /** 写入采集结果；空值不覆盖已有值（rkey 只在过期后才会被新值替换）。 */
    fun update(friend: String, group: String) {
        if (friend.isNotEmpty()) friendRkey = friend
        if (group.isNotEmpty()) groupRkey = group
    }

    /**
     * 挂载采集点。
     *
     * 纯反射：宿主相关类型（`MsfMessagePair` / `FromServiceMsg`）都没有进 qqinterface 桩，
     * 因此按「方法名 + 参数个数」找方法、按字段类型找 `fromServiceMsg`，
     * 任何一层对不上都只记日志，不影响宿主。
     */
    fun install(hostClassLoader: ClassLoader) {
        if (installed) return

        synchronized(this) {
            if (installed) return

            runCatching {
                val clazz = hostClassLoader.loadClass(RESP_HANDLER_CLASS)
                val method = clazz.declaredMethods.firstOrNull {
                    it.name == "dispatchRespMsg" && it.parameterCount == 4
                } ?: error("找不到 dispatchRespMsg")

                method.isAccessible = true
                method.hookAfter { param ->
                    runCatching { handleResponse(param.args) }
                        .onFailure { LogUtils.androidNoFilter.w("rkey: 响应处理失败", it) }
                }
                installed = true
                LogUtils.androidNoFilter.i("rkey: 已挂载 $RESP_HANDLER_CLASS#dispatchRespMsg")
            }.onFailure {
                LogUtils.androidNoFilter.w("rkey: 挂载失败", it)
            }
        }
    }

    /** 从 dispatchRespMsg 的参数里取 fromServiceMsg，匹配 rkey cmd 才解析。 */
    private fun handleResponse(args: Array<Any?>) {
        val pair = args.getOrNull(1) ?: return
        val fromMsg = readField(pair, "fromServiceMsg") ?: return

        val cmd = readField(fromMsg, "serviceCmd") as? String ?: return
        if (cmd != RKEY_CMD) return

        val buffer = readField(fromMsg, "wupBuffer") as? ByteArray ?: return
        val parsed = parse(buffer) ?: return

        update(parsed.first, parsed.second)
        LogUtils.androidNoFilter.i(
            "rkey: 已更新 friend=${parsed.first.isNotEmpty()} group=${parsed.second.isNotEmpty()}"
        )
    }

    /** 沿继承链按字段名取值。 */
    private fun readField(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            clazz.declaredFields.firstOrNull { it.name == name }?.let { field ->
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            clazz = clazz.superclass
        }
        return null
    }

    /** 供测试重置。 */
    internal fun resetForTest() {
        friendRkey = ""
        groupRkey = ""
    }
}
