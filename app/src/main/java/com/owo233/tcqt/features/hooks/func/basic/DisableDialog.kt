package com.owo233.tcqt.features.hooks.func.basic

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.foundation.data.TCQTBuild
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.loadOrThrow
import com.owo233.tcqt.foundation.internal.QQInterfaces
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.MethodHookParam
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.owo233.tcqt.foundation.utils.isPublic
import com.owo233.tcqt.foundation.utils.paramCount
import com.owo233.tcqt.foundation.utils.proto2json.GlobalJson
import com.tencent.qphone.base.remote.FromServiceMsg
import kotlinx.serialization.Serializable
import mqq.app.Foreground
import java.util.concurrent.ConcurrentHashMap

@RegisterAction
@RegisterSetting(
    key = "disable_dialog",
    name = "屏蔽烦人弹窗",
    type = SettingType.BOOLEAN,
    defaultValue = "true",
    desc = "将一些烦人的弹窗给屏蔽掉，现支持「灰度版本体验」及「社交封禁提醒」弹窗。",
)
class DisableDialog : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        disableGrayCheckDialog()
        disableFekitDialog()
    }

    private fun disableGrayCheckDialog() {
        loadOrThrow("com.tencent.mobileqq.graycheck.business.GrayCheckHandler")
            .declaredMethods.firstOrNull {
                it.isPublic && it.returnType == Void.TYPE &&
                        it.paramCount == 1 && it.parameterTypes[0] == FromServiceMsg::class.java
            }?.hookBeforeMethod { it.result = Unit }
    }

    private fun disableFekitDialog() {
        loadOrThrow("com.tencent.mobileqq.dt.api.impl.DTAPIImpl")
            .hookBeforeMethod(
                "onSecDispatchToAppEvent",
                String::class.java,
                ByteArray::class.java,
            ) { param ->
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
        val appendText = "${TCQTBuild.APP_NAME}模块提醒您，此弹窗在重新启动${HookEnv.appName}前只会展示一次。"

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
