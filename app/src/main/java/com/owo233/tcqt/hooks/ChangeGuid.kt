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
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.hooks.helper.GuidHelper
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.SpManager
import com.owo233.tcqt.utils.logD
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

@RegisterAction
class ChangeGuid: IAction {
    override fun onRun(ctx: Context) {
        if (!SpManager.isInit()) {
            SpManager.init(ctx)
        }

        if (PlatformTools.isMsfProcess()) {
            if (SpManager.contains(SpManager.SP_KEY_GUID) &&
                SpManager.getString(SpManager.SP_KEY_GUID, "").isNotBlank()) {
                logD(tag = "Guid Hook", msg = "已设置自定义的GUID: ${SpManager.getString(SpManager.SP_KEY_GUID)}")
                GuidHelper.hookGuid(SpManager.getString(SpManager.SP_KEY_GUID))
            } else {
                logD(tag = "Guid Hook", msg = "没有设置自定义的GUID,无需进行修改GUID")
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

                    // logD("Guid Hook", "onCreate: $className")

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
                                    setText(SpManager.getString(SpManager.SP_KEY_GUID, ""))
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
                                        val guid = input.text.toString().trim()

                                        if (guid.isBlank()) {
                                            SpManager.remove(SpManager.SP_KEY_GUID)
                                            Toast.makeText(activity, "已移除自定义GUID，重启宿主生效", Toast.LENGTH_SHORT).show()
                                        } else if (!guid.matches(Regex("^[a-fA-F0-9]{32}$"))) {
                                            Toast.makeText(activity, "GUID 格式不正确", Toast.LENGTH_SHORT).show()
                                            return@setPositiveButton
                                        } else {
                                            SpManager.setString(SpManager.SP_KEY_GUID, guid)
                                            Toast.makeText(activity, "自定义的GUID已保存，重启宿主生效", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNeutralButton("恢复") { _, _ ->
                                        if (SpManager.contains(SpManager.SP_KEY_GUID)) {
                                            SpManager.remove(SpManager.SP_KEY_GUID)
                                            Toast.makeText(activity, "已清除自定义GUID，重启宿主生效", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNegativeButton("取消", null)
                                    .create()

                                dialog.show()

                                val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                neutralButton.isEnabled = SpManager.contains(SpManager.SP_KEY_GUID)

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

    override val name: String get() = "自定义GUID"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)
}
