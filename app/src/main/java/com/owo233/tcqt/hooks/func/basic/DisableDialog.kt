package com.owo233.tcqt.hooks.func.basic

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.isPublic
import com.owo233.tcqt.utils.hook.paramCount
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.proto2json.GlobalJson
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qphone.base.remote.FromServiceMsg
import kotlinx.serialization.Serializable
import mqq.app.Foreground
import java.util.concurrent.ConcurrentHashMap

@RegisterAction
@RegisterSetting(
    key = "disable_dialog",
    name = "屏蔽烦人弹窗",
    type = SettingType.BOOLEAN,
    desc = "将一些烦人的弹窗给屏蔽掉，现支持「版本升级弹窗」及「灰度版本体验」及「社交封禁提醒」弹窗。",
)
@RegisterSetting(
    key = "disable_dialog.type",
    name = "可选项",
    type = SettingType.INT_MULTI,
    defaultValue = "0",
    options = "屏蔽灰度版本体验|屏蔽社交封禁提醒|屏蔽版本升级弹窗"
)
class DisableDialog : IAction {

    private val options: Int by lazy {
        GeneratedSettingList.getInt(GeneratedSettingList.DISABLE_DIALOG_TYPE)
    }

    override fun onRun(app: Application, process: ActionProcess) {
        val actionMap = mapOf(
            0 to ::disableGrayCheckDialog,
            1 to ::disableFekitDialog,
            2 to ::disableNewVersionDialog
        )

        actionMap.forEach { (flag, action) ->
            if (options.isFlagEnabled(flag)) action()
        }
    }

    private fun disableNewVersionDialog() {
        loadOrThrow("com.tencent.mobileqq.upgrade.ui.dialog.UpgradeActivity").findMethod {
            name = "doOnCreate"
            paramTypes = arrayOf(bundle)
        }.hookReplace { param ->
            (param.thisObject as Activity).finish()
            true
        }

        loadOrThrow("com.tencent.biz.qui.noticebar.view.VQUINoticeBarLayout")
            .getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
            .hookAfter { param ->
                val view = param.thisObject as FrameLayout
                view.visibility = View.GONE
                view.layoutParams = FrameLayout.LayoutParams(0, 0)
            }
    }

    private fun disableGrayCheckDialog() {
        loadOrThrow("com.tencent.mobileqq.graycheck.business.GrayCheckHandler")
            .declaredMethods.firstOrNull {
                it.isPublic && it.returnType == Void.TYPE &&
                        it.paramCount == 1 && it.parameterTypes[0] == FromServiceMsg::class.java
            }?.hookBefore { it.result = Unit }
    }

    private fun disableFekitDialog() {
        loadOrThrow("com.tencent.mobileqq.dt.api.impl.DTAPIImpl")
            .hookMethodBefore(
                "onSecDispatchToAppEvent",
                String::class.java,
                ByteArray::class.java,
            ) { param ->
                if (!QQInterfaces.isLogin) return@hookMethodBefore

                val currentUin = QQInterfaces.currentUin
                val currentIsShow = isShowMap[currentUin] ?: false
                val shouldUpdateShow = handleSocialErrorOptimized(
                    param,
                    currentIsShow,
                    ::updateWordingAndGenerateNewJson
                )
                if (shouldUpdateShow) {
                    isShowMap[currentUin] = true
                }
            }
    }

    private fun handleSocialErrorOptimized(
        param: MethodHookParam,
        currentIsShow: Boolean,
        updateWordingAndGenerateNewJson: (String) -> String
    ): Boolean {
        val type = param.args.getOrNull(0) as? String ?: return false
        if (type != "socialError") return false

        if (currentIsShow) {
            param.result = Unit
            return false
        }

        val jsonString = (param.args.getOrNull(1) as? ByteArray)?.toString(Charsets.UTF_8)
            ?: return false

        val shouldProceed = Foreground.isCurrentProcessForeground() && // 进程在前台
                Foreground.getTopActivity()?.run { // 顶层 Activity 存在且不是正在销毁
                    !isFinishing && !isDestroyed
                } ?: false

        return if (shouldProceed) {
            param.args[1] = updateWordingAndGenerateNewJson(jsonString).toByteArray(Charsets.UTF_8)
            true
        } else {
            param.result = Unit
            false
        }
    }

    private fun updateWordingAndGenerateNewJson(jsonString: String): String {
        val appendText =
            "${TCQTBuild.APP_NAME}模块提醒您，此弹窗在重新启动${HookEnv.appName}前只会展示一次。"

        val originalReminder: SafetyReminder = try {
            GlobalJson.decodeFromString(SafetyReminder.serializer(), jsonString)
        } catch (e: Exception) {
            Log.e("无法解析社交封禁JSON!", e)
            return jsonString
        }

        val newWording = "${originalReminder.wording}$appendText"
        val modifiedReminder = originalReminder.copy(
            wording = newWording,
            title = "${originalReminder.title}弹窗"
        )
        val newJsonString = GlobalJson.encodeToString(
            SafetyReminder.serializer(),
            modifiedReminder
        )

        return newJsonString
    }

    @Serializable
    data class SafetyReminder(
        val uin: String = "",
        val wording: String = "",
        val title: String = "",
        val buttons: List<Button> = emptyList()
    )

    @Serializable
    data class Button(
        val wording: String = "",
        val url: String = "",
        val jumpType: Int = 0,
        val color: Int = 0
    )

    companion object {
        private val isShowMap = ConcurrentHashMap<String, Boolean>()
    }

    override val key: String get() = GeneratedSettingList.DISABLE_DIALOG

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
