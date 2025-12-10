package com.owo233.tcqt.hooks

import android.app.Activity
import android.app.AlertDialog
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
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.GuidHelper
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hookAfterMethod
import de.robv.android.xposed.XposedHelpers

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
        val defaultGuid = QQInterfaces.guid
        if (defaultGuid.isNotBlank() && defaultGuid != "null") {
            GuidConfig.defaultGuid = defaultGuid
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
        val clazz = XposedHelpers.findClass("mqq.app.AppActivity", HookEnv.hostClassLoader)
        clazz.hookAfterMethod("onCreate", Bundle::class.java) { param ->
            val activity = param.thisObject as Activity
            if (!activity.javaClass.name.contains("Login")) return@hookAfterMethod

            activity.window.decorView.rootView.post {
                findLoginButton(activity.window.decorView.rootView)?.apply {
                    setOnLongClickListener {
                        showGuidDialog(activity)
                        true
                    }
                }
            }
        }
    }

    private fun showGuidDialog(activity: Activity) {
        val input = EditText(activity).apply {
            hint = "32 位 GUID（可为空）"
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minHeight = (48 * resources.displayMetrics.density).toInt()
            filters = arrayOf(InputFilter.LengthFilter(32))
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setSingleLine()
            setPadding(50, 36, 50, 36)
            setText(if (GuidConfig.isEnabled) GuidConfig.newGuid else GuidConfig.defaultGuid)
        }

        val container = FrameLayout(activity).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT )
            val margin = (20 * activity.resources.displayMetrics.density).toInt()
            setPadding(margin, margin, margin, margin)
            addView(input, params)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("设置自定义 GUID")
            .setView(container)
            .setPositiveButton("保存") { _, _ -> handleSaveGuid(activity, input.text.toString().trim()) }
            .setNeutralButton("恢复") { _, _ -> handleRestoreGuid(activity) }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = GuidConfig.isEnabled
    }

    private fun handleSaveGuid(activity: Activity, guid: String) {
        when {
            guid.isBlank() -> {
                GuidConfig.disable()
                toastAndRestart(activity, "已禁用自定义GUID，立即生效")
            }
            !guid.matches(Regex("^[a-fA-F0-9]{32}$")) -> {
                toast(activity, "GUID 格式不正确")
            }
            guid.equals(GuidConfig.newGuid, true) && GuidConfig.isEnabled -> {
                toast(activity, "GUID 与当前自定义一致，无需修改")
            }
            guid.equals(GuidConfig.defaultGuid, true) -> {
                if (GuidConfig.isEnabled) {
                    GuidConfig.disable()
                    toastAndRestart(activity, "已还原为系统默认，立即生效")
                } else {
                    toast(activity, "与系统默认值一致，无需重复设置")
                }
            }
            else -> {
                GuidConfig.enableWith(guid)
                toastAndRestart(activity, "已保存，立即生效")
            }
        }
    }

    private fun handleRestoreGuid(activity: Activity) {
        if (GuidConfig.isEnabled) {
            GuidConfig.disable()
            toastAndRestart(activity, "已恢复默认，立即生效")
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
