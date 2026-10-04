package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces
import java.lang.reflect.Method

/**
 * 名片点赞（`sendZan`）。
 *
 * 宿主入口是 `com.tencent.mobileqq.app.CardHandler` 上的
 * `d4(JJ[BIII)V`（QQ 9.3.70 实际签名，方法名被混淆）。
 * 与 [ScriptPai] 一样**按参数签名找**，不按方法名找。
 */
internal object ScriptZan {

    private const val HANDLER_CLASS = "com.tencent.mobileqq.app.CardHandler"

    /** 形如 `(long, long, byte[], int, int, int)` 且返回 void。 */
    private val handler: Method? by lazy {
        runCatching {
            // 必须用宿主类加载器：CardHandler 只存在于宿主 APK 里。
            val clazz = HookEnv.hostClassLoader.loadClass(HANDLER_CLASS)
            clazz.declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                        method.parameterCount == 6 &&
                        method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                        method.parameterTypes[1] == Long::class.javaPrimitiveType &&
                        method.parameterTypes[2] == ByteArray::class.java &&
                        method.parameterTypes[3] == Integer.TYPE &&
                        method.parameterTypes[4] == Integer.TYPE &&
                        method.parameterTypes[5] == Integer.TYPE
            }?.apply { isAccessible = true }
        }.onFailure { LogUtils.androidNoFilter.w("脚本 sendZan: 找不到 CardHandler", it) }
            .getOrNull()
    }

    /**
     * 送赞。
     *
     * @param uin   目标 QQ 号
     * @param count 次数
     */
    fun send(uin: String, count: Int, isFriend: Boolean) {
        val method = handler ?: run {
            LogUtils.androidNoFilter.w("脚本 sendZan 不支持：宿主没有可用签名")
            return
        }

        val selfUin = runCatching { QQInterfaces.currentUin.toLong() }.getOrNull() ?: run {
            LogUtils.androidNoFilter.w("脚本 sendZan: 取不到本机 QQ 号")
            return
        }
        val targetUin = uin.toLongOrNull() ?: run {
            LogUtils.androidNoFilter.w("脚本 sendZan: 目标 QQ 号非法 $uin")
            return
        }

        runCatching {
            val instance = ScriptPai.handlerInstance(method.declaringClass)
                ?: error("拿不到 CardHandler 实例")

            method.invoke(
                instance,
                selfUin,
                targetUin,
                requestData(isFriend),
                if (isFriend) TYPE_FRIEND else TYPE_STRANGER,
                count,
                0,
            )
        }.onFailure {
            LogUtils.androidNoFilter.e("脚本 sendZan 失败: $uin x$count", it)
        }
    }

    /**
     * 点赞请求的固定前缀。
     *
     * 末位随好友/陌生人变化：好友 49，非好友 53。
     */
    private fun requestData(isFriend: Boolean): ByteArray = byteArrayOf(
        12, 24, 0, 1, 6, 1, 49, 22, 1, if (isFriend) 49 else 53,
    )

    /** 好友来源。 */
    private const val TYPE_FRIEND = 1

    /** 陌生人来源。 */
    private const val TYPE_STRANGER = 5
}
