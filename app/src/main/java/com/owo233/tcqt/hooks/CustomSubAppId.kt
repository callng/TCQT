package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.logI
import com.tencent.common.config.AppSetting
import de.robv.android.xposed.XposedBridge
import kotlin.Boolean
import kotlin.String

// @RegisterAction
class CustomSubAppId: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XposedBridge.hookMethod(
            AppSetting::class.java.getDeclaredMethod("f")
            , afterHook { param ->
                param.result = subAppId.toInt()
            })
    }

    companion object {
        private val subAppId: String by TCQTSetting.getSetting<String>(TCQTSetting.CUSTOM_SUBAPPID_STRING)

        private val isOff: Boolean by lazy {
            return@lazy TCQTSetting.isAppIdDisable
        }

        private val isSubAppIdValid: Boolean
            get() {
                val trimmed = subAppId.trim()
                return trimmed
                    .takeIf { it.isNotEmpty() && trimmed.lines().size == 1 }
                    ?.takeIf { it.startsWith("537") && it.length == 9 }
                    ?.toIntOrNull()
                    ?.let { it > 0 }
                    ?: false
            }
    }

    override val name: String get() = "自定义SubAppId"

    override val key: String get() = TCQTSetting.CUSTOM_SUBAPPID

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)

    override fun canRun(): Boolean {
        val isEnabled: Boolean = TCQTSetting.getSetting<Boolean>(key)
            .getValue(this, ::key) && isSubAppIdValid

        if (isEnabled && isOff) {
            TCQTSetting.getSetting<Any>(key).setValue(this, ::key, false)
            logI(msg = "已通过isAppIdDisable文件关闭自定义SubAppId功能")
            return false
        }

        return isEnabled
    }
}
