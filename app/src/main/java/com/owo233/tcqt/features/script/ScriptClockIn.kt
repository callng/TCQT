package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces

/**
 * 群打卡（`clockIn`）。
 *
 * 宿主 `com.tencent.mobileqq.troop.clockin.handler.TroopClockInHandler#y0(String, String)`
 * 是对 `Q2(String, String, int, boolean)` 的转发壳（`y0` 内部固定传 `0, true`），
 * 参数是 **(群号, 打卡者QQ号)**。
 *
 * 方法名被混淆，因此按「参数为 (String, String) 且返回 void、且类里有 clockin 特征」找；
 * 找不到只记日志，不打断脚本。
 */
internal object ScriptClockIn {

    private const val HANDLER_CLASS = "com.tencent.mobileqq.troop.clockin.handler.TroopClockInHandler"

    private val handler: java.lang.reflect.Method? by lazy {
        runCatching {
            val clazz = HookEnv.hostClassLoader.loadClass(HANDLER_CLASS)
            clazz.declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == String::class.java
            }?.apply { isAccessible = true }
        }.onFailure { LogUtils.androidNoFilter.w("脚本 clockIn: 找不到 TroopClockInHandler", it) }
            .getOrNull()
    }

    /** @param groupUin 群号 */
    fun clockIn(groupUin: String) {
        val method = handler ?: run {
            LogUtils.androidNoFilter.w("脚本 clockIn 不支持：宿主没有可用签名")
            return
        }

        val selfUin = runCatching { QQInterfaces.currentUin }.getOrNull().orEmpty()
        if (selfUin.isEmpty()) {
            LogUtils.androidNoFilter.w("脚本 clockIn: 取不到本机 QQ 号")
            return
        }

        runCatching {
            val instance = ScriptPai.handlerInstance(method.declaringClass)
                ?: error("拿不到 TroopClockInHandler 实例")
            method.invoke(instance, groupUin, selfUin)
        }.onFailure {
            LogUtils.androidNoFilter.e("脚本 clockIn 失败: group=$groupUin", it)
        }
    }
}
