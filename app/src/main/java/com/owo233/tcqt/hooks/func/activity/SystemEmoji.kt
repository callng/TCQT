package com.owo233.tcqt.hooks.func.activity

// 思路参考自 QAuxiliary: me.singleneuron.hook.SystemEmoji
// https://github.com/cinit/QAuxiliary

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.isStatic
import java.lang.reflect.Method

@RegisterAction
class SystemEmoji : IAction {

    override val name: String get() = "强制使用系统 Emoji"
    override val desc: String get() = "禁用 QQ 内置 Emoji 映射，让文本中的 Emoji 使用系统字体渲染。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "system_emoji"

    override fun onRun(app: Application, process: ActionProcess) {
        val emotcationConstants = loadOrThrow("com.tencent.mobileqq.text.EmotcationConstants")

        listOf(
            emotcationConstants.findUniqueIntMethod(paramCount = 1),
            emotcationConstants.findUniqueIntMethod(paramCount = 2)
        ).forEach { method ->
            method.hookBefore { param ->
                param.result = -1
            }
        }
    }

    private fun Class<*>.findUniqueIntMethod(paramCount: Int): Method {
        val candidates = declaredMethods.filter { method ->
            method.isStatic &&
                method.returnType == Integer.TYPE &&
                method.parameterTypes.size == paramCount &&
                method.parameterTypes.all { it == Integer.TYPE }
        }

        return candidates.single().apply { isAccessible = true }
    }
}
