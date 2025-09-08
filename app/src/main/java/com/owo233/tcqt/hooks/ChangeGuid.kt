package com.owo233.tcqt.hooks

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.GuidHelper
import com.owo233.tcqt.internals.helper.GuildHelper
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.SpManager
import com.owo233.tcqt.utils.logI
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

@RegisterAction
@RegisterSetting(
    key = "change_guid",
    name = "自定义GUID",
    type = SettingType.BOOLEAN,
    defaultValue = "true",
    desc = "启用后在登录页面长按登录按钮即可调出设置窗口。",
    isRedMark = true,
    uiOrder = 1
)
class ChangeGuid : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        if (!SpManager.isInit()) {
            SpManager.init(ctx)
        }

        val defaultGuid = GuildHelper.getGuidHex()
        if (!defaultGuid.isBlank() && defaultGuid != "null") {
            SpManager.setString(SpManager.SP_KEY_DEFAULT_GUID, defaultGuid)
        }

        if (PlatformTools.isMsfProcess()) {
            if (SpManager.getString(SpManager.SP_KEY_NEW_GUID, "").isNotBlank() &&
                SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)) {
                logI(msg = """
                    
                    设置GUID: ${SpManager.getString(SpManager.SP_KEY_NEW_GUID)}
                    原始GUID: ${SpManager.getString(SpManager.SP_KEY_DEFAULT_GUID)}
                    
                """.trimIndent())
                GuidHelper.hookGuid(SpManager.getString(SpManager.SP_KEY_NEW_GUID))
            }
        }

        if (PlatformTools.isMainProcess()) {
            val clazz = XposedHelpers.findClass("mqq.app.AppActivity", XpClassLoader)

            XposedBridge.hookAllMethods(
                clazz,
                "onCreate",
                afterHook { param ->
                    val activity = param.thisObject as Activity
                    val className = activity.javaClass.name

                    if (!className.contains("Login")) return@afterHook

                    activity.window.decorView.rootView.post {
                        findLoginButton(activity.window.decorView.rootView)?.let {
                            it.setOnLongClickListener {
                                val input = EditText(activity).apply {
                                    hint = "32 位 GUID（可为空）"
                                    inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    minHeight = (48 * resources.displayMetrics.density).toInt()
                                    filters = arrayOf(InputFilter.LengthFilter(32))
                                    textSize = 13f
                                    gravity = Gravity.CENTER_HORIZONTAL
                                    setText(
                                        if (SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false))
                                        SpManager.getString(SpManager.SP_KEY_NEW_GUID, "无法获取被自定义的GUID")
                                        else SpManager.getString(SpManager.SP_KEY_DEFAULT_GUID, "无法获取默认的GUID")
                                    )
                                    setSingleLine()
                                    setPadding(50, 36, 50, 36)
                                }

                                val container = FrameLayout(activity).apply {
                                    val params = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    val margin = (20 * activity.resources.displayMetrics.density).toInt()
                                    setPadding(margin, margin, margin, margin)
                                    addView(input, params)
                                }

                                val dialog = AlertDialog.Builder(activity)
                                    .setTitle("设置自定义 GUID")
                                    .setView(container)
                                    .setPositiveButton("保存") { _, _ ->
                                        val guid = input.text.toString().trim().lowercase()

                                        if (guid.isBlank()) {
                                            SpManager.setBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)
                                            PlatformTools.restartMsfProcess()
                                            Toast.makeText(activity, "已禁用自定义GUID，立即生效", Toast.LENGTH_SHORT).show()
                                            return@setPositiveButton
                                        }

                                        if (!guid.matches(Regex("^[a-fA-F0-9]{32}$"))) {
                                            Toast.makeText(activity, "GUID 格式不正确", Toast.LENGTH_SHORT).show()
                                            return@setPositiveButton
                                        }

                                        val currentCustomGuid = SpManager.getString(SpManager.SP_KEY_NEW_GUID, "").lowercase()
                                        val defaultGuid = SpManager.getString(SpManager.SP_KEY_DEFAULT_GUID, "").lowercase()

                                        if (guid == currentCustomGuid && SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)) {
                                            Toast.makeText(activity, "GUID 与当前自定义一致，无需修改", Toast.LENGTH_SHORT).show()
                                            return@setPositiveButton
                                        }

                                        if (guid == defaultGuid) {
                                            if (SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)) {
                                                SpManager.setBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)
                                                PlatformTools.restartMsfProcess()
                                                Toast.makeText(activity, "已还原为系统默认，立即生效", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(activity, "与系统默认值一致，无需重复设置", Toast.LENGTH_SHORT).show()
                                            }
                                            return@setPositiveButton
                                        }

                                        SpManager.setString(SpManager.SP_KEY_NEW_GUID, guid)
                                        SpManager.setBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, true)
                                        PlatformTools.restartMsfProcess()
                                        Toast.makeText(activity, "已保存，立即生效", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNeutralButton("恢复") { _, _ ->
                                        if (SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)) {
                                            SpManager.setBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)
                                            PlatformTools.restartMsfProcess()
                                            Toast.makeText(activity, "已清除自定义，立即生效", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNegativeButton("取消", null)
                                    .create()

                                dialog.show()

                                val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                neutralButton.isEnabled = SpManager.getBoolean(SpManager.SP_KEY_CHANGE_GUID_ENABLED, false)

                                true
                            }
                        }
                    }
                }
            )
        }
    }

    private fun findLoginButton(view: View): Button? {
        if (view is Button && view.text?.contains("登录") == true) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val button = findLoginButton(child)
                if (button != null) return button
            }
        }
        return null
    }

    override val key: String get() = GeneratedSettingList.CHANGE_GUID

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)
}
