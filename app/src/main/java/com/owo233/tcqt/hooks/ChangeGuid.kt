package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.GuidHelper
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.ContextUtils
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.context.HostContextFactory
import com.owo233.tcqt.utils.hookAfterMethod

@RegisterAction
@RegisterSetting(
    key = "change_guid",
    name = "自定义GUID",
    type = SettingType.BOOLEAN,
    defaultValue = "true",
    desc = "启用后在登录页面长按登录按钮即可调出设置窗口。",
    uiTab = "高级"
)
@RegisterSetting(key = "change_guid.string.defaultGuid", name = "默认GUID", type = SettingType.STRING, hidden = true)
@RegisterSetting(key = "change_guid.string.newGuid", name = "新GUID", type = SettingType.STRING, hidden = true)
@RegisterSetting(
    key = "change_guid.boolean.isEnabled",
    name = "是否启用更改",
    type = SettingType.BOOLEAN,
    defaultValue = "false"
)
class ChangeGuid : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        initDefaultGuid()

        when {
            PlatformTools.isMsfProcess() -> setupMsfHook()
            PlatformTools.isMainProcess() -> setupLoginUiHook()
        }
    }

    private fun initDefaultGuid() {
        val guid = GuidConfig.defaultGuid
        if (guid.isEmpty()) {
            val defaultGuid = QQInterfaces.guid
            if (defaultGuid.isNotEmpty() && defaultGuid != "null") {
                GuidConfig.defaultGuid = defaultGuid
            }
        }
    }

    private fun setupMsfHook() {
        if (GuidConfig.isEnabled && GuidConfig.newGuid.isNotBlank()) {
            Log.i("""

                设置GUID: ${GuidConfig.newGuid}
                原始GUID: ${GuidConfig.defaultGuid}

            """.trimIndent())

            GuidHelper.hookGuid(GuidConfig.newGuid)
        }
    }

    private fun setupLoginUiHook() {
        loadOrThrow("mqq.app.AppActivity").hookAfterMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val activity = param.thisObject as Activity
            if (!activity.javaClass.name.contains("Login")) return@hookAfterMethod
            activity.window.decorView.rootView.post {
                findLoginButton(activity.window.decorView.rootView)?.apply {
                    setOnLongClickListener {
                        val context = HostContextFactory.createMaterialContext(
                            ContextUtils.getCurrentActivity()!!
                        )
                        showGuidDialog(context)
                        true
                    }
                }
            }
        }
    }

    private fun showGuidDialog(context: Context) {
        val input = EditText(context).apply {
            hint = "32 位 GUID（可为空）"
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minHeight = (48 * resources.displayMetrics.density).toInt()
            filters = arrayOf(InputFilter.LengthFilter(32))
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setSingleLine()
            setPadding(50, 36, 50, 36)
            setText(if (GuidConfig.isEnabled) GuidConfig.newGuid else GuidConfig.defaultGuid)
        }

        val container = FrameLayout(context).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT )
            val margin = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(margin, margin, margin, margin)
            addView(input, params)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("设置自定义 GUID")
            .setView(container)
            .setPositiveButton("保存") { _, _ -> handleSaveGuid(context, input.text.toString().trim()) }
            .setNeutralButton("恢复") { _, _ -> handleRestoreGuid(context) }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
        dialog.getButton(-3).isEnabled = GuidConfig.isEnabled
    }

    private fun handleSaveGuid(context: Context, guid: String) {
        when {
            guid.isBlank() -> {
                GuidConfig.disable()
                toastAndRestart(context, "已禁用自定义GUID，立即生效")
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
                    toastAndRestart(context, "已还原为系统默认，立即生效")
                } else {
                    toast(context, "与系统默认值一致，无需重复设置")
                }
            }
            else -> {
                GuidConfig.enableWith(guid)
                toastAndRestart(context, "已保存，立即生效")
            }
        }
    }

    private fun handleRestoreGuid(context: Context) {
        if (GuidConfig.isEnabled) {
            GuidConfig.disable()
            toastAndRestart(context, "已恢复默认，立即生效")
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    private fun toastAndRestart(context: Context, msg: String) {
        toast(context, msg)
        PlatformTools.restartMsfProcess()
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

    override val key: String get() = GeneratedSettingList.CHANGE_GUID

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)
}

private object GuidConfig {
    var defaultGuid: String
        get() = GeneratedSettingList.getString(GeneratedSettingList.CHANGE_GUID_STRING_DEFAULTGUID)
        set(value) = GeneratedSettingList.setString(GeneratedSettingList.CHANGE_GUID_STRING_DEFAULTGUID, value)

    var newGuid: String
        get() = GeneratedSettingList.getString(GeneratedSettingList.CHANGE_GUID_STRING_NEWGUID)
        set(value) = GeneratedSettingList.setString(GeneratedSettingList.CHANGE_GUID_STRING_NEWGUID, value)

    var isEnabled: Boolean
        get() = GeneratedSettingList.getBoolean(GeneratedSettingList.CHANGE_GUID_BOOLEAN_ISENABLED)
        set(value) = GeneratedSettingList.setBoolean(GeneratedSettingList.CHANGE_GUID_BOOLEAN_ISENABLED, value)

    fun enableWith(guid: String) {
        newGuid = guid.lowercase()
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }
}
