package com.owo233.tcqt.ext

import android.content.Context
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.logE

enum class ActionProcess {
    MSF, MAIN, TOOL, OPENSDK,
    OTHER, // 非上述四个
    ALL, // 全部进程
}

/**
 * 无视设置开关条件的Action
 */
abstract class AlwaysRunAction : IAction {
    override val key: String
        get() = ""

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean = true
}

interface IAction {
    operator fun invoke(ctx: Context) {
        runCatching {
            if (canRun()) onRun(ctx)
        }.onFailure {
            logE(msg = "invoke Action 异常", cause = it)
        }
    }

    fun onRun(ctx: Context)

    fun canRun(): Boolean {
        val setting by TCQTSetting.getSetting<Boolean>(key)
        return setting
    }

    val name: String

    val key: String

    val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN)
}
