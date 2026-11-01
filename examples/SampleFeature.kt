package com.owo233.tcqt.features.debug

import android.app.Activity
import com.owo233.tcqt.annotations.ActionPriority
import com.owo233.tcqt.annotations.ActionProcess
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.hook.hookBefore

/** Compiled in Debug only. Keep disabled until copied into features/ for an intentional feature. */
@RegisterAction(
    enabled = false,
    processes = [ActionProcess.MAIN],
    priority = ActionPriority.DEFERRED,
)
object SampleFeature : Feature(
    key = "sample_activity_title",
    name = "示例：窗口标题后缀",
    desc = "为宿主设置的窗口标题追加文字。",
) {
    // Persisted key: sample_activity_title_suffix. Read the delegated String directly.
    private var suffix by stringOption(
        settingKey = "suffix",
        name = "后缀",
        defaultValue = "TCQT",
    )

    override fun install() {
        Activity::class.java.getDeclaredMethod("setTitle", CharSequence::class.java).hookBefore { param ->
            val title = param.args[0] as? CharSequence ?: return@hookBefore
            val ending = suffix
            if (ending.isNotBlank()) param.args[0] = "$title $ending"
        }
    }
}
