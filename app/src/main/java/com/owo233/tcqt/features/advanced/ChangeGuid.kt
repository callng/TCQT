package com.owo233.tcqt.features.advanced

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.host.QQInterfaces

/**
 * GUID 在登录 / 设备注册流程早期被读取（不会在 onCreate 内），故放在 EARLY。
 */
@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MSF], priority = ActionPriority.EARLY)
object ChangeGuid : Feature(
    key = "change_guid",
    name = "自定义GUID",
    desc = "在登录页面长按登录按钮即可调出设置窗口，如果你不知道GUID是什么，请勿使用此功能（此处开关仅作为全局开关）。",
    defaultEnabled = true,
) {

    // isHide = true 必须保留，否则这两个输入框会出现在「高级」分类里。
    private var defaultGuid by stringOption(
        settingKey = "string.defaultGuid",
        name = "默认GUID",
        isHide = true,
    )

    private var newGuid by stringOption(
        settingKey = "string.newGuid",
        name = "新GUID",
        isHide = true,
    )

    private var isEnabled by booleanOption(
        settingKey = "boolean.isEnabled",
        name = "是否启用更改",
    )

    override fun install() {
        when {
            PlatformTools.isMsfProcess() -> setupGuidHook()
            PlatformTools.isMainProcess() -> {
                setupGuidHook()
                setupLoginUiHook()
            }
        }
    }

    private fun setupGuidHook() {
        if (isEnabled && newGuid.isNotBlank()) {
            GuidHook.hookGuid(newGuid)
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
        if (defaultGuid.isEmpty()) {
            val current = QQInterfaces.guid
            if (current.isNotEmpty() && current != "null") {
                defaultGuid = current
            }
        }
    }

    private fun showGuidDialog(context: Context) {
        val currentDisplayGuid = if (isEnabled) {
            newGuid
        } else {
            defaultGuid.ifEmpty { "无法获取原始GUID" }
        }

        GuidEditorDialog(
            context = context,
            initialGuid = currentDisplayGuid,
            restoreEnabled = isEnabled,
            onSave = { handleSaveGuid(context, it) },
            onRestore = { handleRestoreGuid(context) },
        ).show()
    }

    private fun handleSaveGuid(context: Context, guid: String) {
        ensureDefaultGuidInitialized()

        when {
            guid.isBlank() -> {
                disable()
                toastAndRestart(context, "已禁用自定义GUID")
            }

            !guid.matches(Regex("^[a-fA-F0-9]{32}$")) -> {
                toast(context, "GUID 格式不正确")
            }

            guid.equals(newGuid, true) && isEnabled -> {
                toast(context, "GUID 与当前自定义一致，无需修改")
            }

            guid.equals(defaultGuid, true) -> {
                if (isEnabled) {
                    disable()
                    toastAndRestart(context, "已还原为系统默认值")
                } else {
                    toast(context, "与系统默认值一致，无需重复设置")
                }
            }

            else -> {
                enableWith(guid)
                toastAndRestart(context, "已保存")
            }
        }
    }

    private fun handleRestoreGuid(context: Context) {
        if (isEnabled) {
            disable()
            toastAndRestart(context, "已恢复默认值")
        }
    }

    private fun enableWith(guid: String) {
        newGuid = guid.lowercase()
        isEnabled = true
    }

    private fun disable() {
        isEnabled = false
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
