package com.owo233.tcqt.hooks.func.advanced

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.BooleanSetting
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.GuidHelper
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.ui.GuidEditorDialog
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.hookMethodAfter

@RegisterAction
class ChangeGuid : IAction {

    override val name: String get() = "自定义GUID"
    override val defaultEnabled: Boolean get() = true
    override val desc: String get() = "启用后在登录页面长按登录按钮即可调出设置窗口，这个功能使用不当可能会导致用户身份信息失效需重新登录。"
    override val uiTab: String get() = "高级"
    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting("change_guid.string.defaultGuid", "默认GUID", "", "", "", false),
            StringSetting("change_guid.string.newGuid", "新GUID", "", "", "", false),
            BooleanSetting("change_guid.boolean.isEnabled", "是否启用更改", false, ""),
        )

    override val key: String
        get() = "change_guid"

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)

    override fun onRun(app: Application, process: ActionProcess) {
        when {
            PlatformTools.isMsfProcess() -> setupGuidHook()
            PlatformTools.isMainProcess() -> {
                setupGuidHook()
                setupLoginUiHook()
            }
        }
    }

    private fun setupGuidHook() {
        if (GuidConfig.isEnabled && GuidConfig.newGuid.isNotBlank()) {
            GuidHelper.hookGuid(GuidConfig.newGuid)
        }
    }

    private fun setupLoginUiHook() {
        loadOrThrow("mqq.app.AppActivity").hookMethodAfter(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val activity = param.thisObject as Activity
            if (!activity.javaClass.name.contains("Login")) return@hookMethodAfter
            activity.window.decorView.rootView.post {
                findLoginButton(activity.window.decorView.rootView)?.apply {
                    setOnLongClickListener {
                        ensureDefaultGuidInitialized()

                        showGuidDialog(activity)
                        true
                    }
                }
            }
        }
    }

    private fun ensureDefaultGuidInitialized() {
        if (GuidConfig.defaultGuid.isEmpty()) {
            val defaultGuid = QQInterfaces.guid
            if (defaultGuid.isNotEmpty() && defaultGuid != "null") {
                GuidConfig.defaultGuid = defaultGuid
            }
        }
    }

    private fun showGuidDialog(context: Context) {
        val currentDisplayGuid = if (GuidConfig.isEnabled) {
            GuidConfig.newGuid
        } else {
            GuidConfig.defaultGuid.ifEmpty { "无法获取原始GUID" }
        }

        GuidEditorDialog(
            context = context,
            initialGuid = currentDisplayGuid,
            restoreEnabled = GuidConfig.isEnabled,
            onSave = { handleSaveGuid(context, it) },
            onRestore = { handleRestoreGuid(context) },
        ).show()
    }

    private fun handleSaveGuid(context: Context, guid: String) {
        ensureDefaultGuidInitialized()

        when {
            guid.isBlank() -> {
                GuidConfig.disable()
                toastAndRestart(context, "已禁用自定义GUID")
            }

            !guid.matches(Regex("^[a-fA-F0-9]{32}$")) -> {
                toast(context, "GUID 格式不正确")
            }

            guid.equals(GuidConfig.newGuid, true) && GuidConfig.isEnabled -> {
                toast(context, "GUID 与当前自定义一致，无需修改")
            }

            guid.equals(GuidConfig.defaultGuid, true) -> {
                if (GuidConfig.isEnabled) {
                    GuidConfig.disable()
                    toastAndRestart(context, "已还原为系统默认值")
                } else {
                    toast(context, "与系统默认值一致，无需重复设置")
                }
            }

            else -> {
                GuidConfig.enableWith(guid)
                toastAndRestart(context, "已保存")
            }
        }
    }

    private fun handleRestoreGuid(context: Context) {
        if (GuidConfig.isEnabled) {
            GuidConfig.disable()
            toastAndRestart(context, "已恢复默认值")
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    private fun toastAndRestart(context: Context, msg: String) {
        toast(context, msg)
        HookEnv.resetApp()
    }

    private fun findLoginButton(view: View): Button? {
        if (view is Button && view.text?.contains("登录") == true) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findLoginButton(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}

private object GuidConfig {

    var defaultGuid: String
        get() = TCQTSetting.getString("change_guid.string.defaultGuid")
        set(value) = TCQTSetting.setString(
            "change_guid.string.defaultGuid",
            value
        )

    var newGuid: String
        get() = TCQTSetting.getString("change_guid.string.newGuid")
        set(value) = TCQTSetting.setString(
            "change_guid.string.newGuid",
            value
        )

    var isEnabled: Boolean
        get() = TCQTSetting.getBoolean("change_guid.boolean.isEnabled")
        set(value) = TCQTSetting.setBoolean(
            "change_guid.boolean.isEnabled",
            value
        )

    fun enableWith(guid: String) {
        newGuid = guid.lowercase()
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }
}
