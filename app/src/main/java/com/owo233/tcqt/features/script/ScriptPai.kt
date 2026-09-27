package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces
import java.lang.reflect.Method

/**
 * 拍一拍（`sendPai`）。
 *
 * 宿主 `PaiYiPaiHandler` 里同名签名有多个转发壳（QQ 9.3.70 上是 `Q2/S2/T2/V2` 等，
 * 名字被混淆且语义相同），因此按**参数签名**找而不是按方法名找；找不到就记日志返回，
 * 不抛异常打断脚本。
 */
internal object ScriptPai {

    private const val HANDLER_CLASS = "com.tencent.mobileqq.paiyipai.PaiYiPaiHandler"

    /** 形如 `(String, String, int)` 或 `(String, String, int, int)`；`void` 返回。 */
    private val handler: Method? by lazy {
        runCatching {
            val clazz = HookEnv.hostClassLoader.loadClass(HANDLER_CLASS)
            clazz.declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                        method.parameterCount in 3..4 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == String::class.java &&
                        method.parameterTypes[2] == Integer.TYPE &&
                        (method.parameterCount == 3 || method.parameterTypes[3] == Integer.TYPE)
            }?.apply { isAccessible = true }
        }.onFailure { LogUtils.androidNoFilter.w("脚本 sendPai: 找不到 PaiYiPaiHandler", it) }
            .getOrNull()
    }

    fun send(chatType: Int, peerUin: String, toUin: String) {
        val method = handler ?: run {
            LogUtils.androidNoFilter.w("脚本 sendPai 不支持：宿主没有可用签名")
            return
        }

        runCatching {
            val instance = handlerInstance(method.declaringClass)
                ?: error("拿不到 PaiYiPaiHandler 实例")

            val args: Array<Any?> = if (method.parameterCount == 4) {
                // 四参版本第二、三位是会话标识，最后一位是业务子类型（0 = 默认）
                arrayOf(peerUin, toUin, chatType, 0)
            } else {
                arrayOf(peerUin, toUin, chatType)
            }
            method.invoke(instance, *args)
        }.onFailure {
            LogUtils.androidNoFilter.e("脚本 sendPai 失败: $peerUin -> $toUin", it)
        }
    }

    /**
     * 从 appRuntime 的 manager 列表里取 handler 实例。
     *
     * 与 [ScriptZan] 共用：两者都是"找不到稳定 getter 的宿主 handler"。
     */
    internal fun handlerInstance(clazz: Class<*>): Any? {
        val runtime = QQInterfaces.appRuntime
        val managerIndexes = 0..16
        managerIndexes.forEach { index ->
            val manager = runCatching { runtime.getManager(index) }.getOrNull()
            if (manager != null && clazz.isInstance(manager)) return manager

            // 部分版本 handler 挂在 manager 的字段里
            manager?.javaClass?.declaredFields?.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(manager)
                    if (value != null && clazz.isInstance(value)) return value
                }
            }
        }
        return null
    }
}
