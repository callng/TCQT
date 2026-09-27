package com.owo233.tcqt.features.script

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionUiType

/**
 * 脚本引擎的设置界面入口（**只负责 UI 入口**）。
 *
 * `ActionSpec.canRun()` 对 `ActionUiType.ENTRY` 恒为 false，因此本类**不能**承担
 * 引擎的安装职责 —— 那会让 `install()` 永不执行。引擎本体是同一包下的
 * [ScriptCore]（[com.owo233.tcqt.api.InfraTask]，始终运行并注入脚本网关）。
 */
@RegisterAction
object ScriptEngine : Feature(
    key = "script_engine",
    name = "脚本引擎",
    desc = "使用 Java 语法（BeanShell）编写脚本，动态扩展模块功能。",
    uiType = ActionUiType.ENTRY,
    processes = setOf(ActionProcess.MAIN),
    requires = Requires(ntOnly = true),
) {

    override fun install() = Unit

    override fun onUiClick(context: Context): Boolean = ScriptLauncher.open(context)
}
