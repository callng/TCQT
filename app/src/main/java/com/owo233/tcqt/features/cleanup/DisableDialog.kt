package com.owo233.tcqt.features.cleanup

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.hook.isPublic
import com.owo233.tcqt.core.hook.paramCount
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.proto.GlobalJson
import com.owo233.tcqt.core.reflect.callMethod
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qphone.base.remote.FromServiceMsg
import kotlinx.serialization.Serializable
import mqq.app.Foreground
import java.util.concurrent.ConcurrentHashMap

@RegisterAction(processes = [ActionProcess.MAIN])
object DisableDialog : Feature(
    key = "disable_dialog",
    name = "屏蔽烦人弹窗",
    desc = "将一些烦人的弹窗给屏蔽掉，现支持「版本升级弹窗」及「灰度版本体验」及「社交封禁提醒」弹窗。",
) {

    private val options by multiIntOption(
        settingKey = "type",
        name = "可选项",
        defaultValue = 0,
        options = listOf("屏蔽灰度版本体验", "屏蔽社交封禁提醒", "屏蔽版本升级弹窗", "屏蔽三方跳转弹窗"),
    )

    private val isShowMap = ConcurrentHashMap<String, Boolean>()

    override fun install() {
        val actionMap = mapOf(
            0 to ::disableGrayCheckDialog,
            1 to ::disableFekitDialog,
            2 to ::disableNewVersionDialog,
            3 to ::disableJumpDialog
        )

        actionMap.forEach { (flag, action) ->
            if (options.isFlagEnabled(flag)) action()
        }
    }

    private fun disableJumpDialog() {
        "com.tencent.mobileqq.haoliyou.JefsClass".toClass.findMethod {
            name = "intercept"
            paramCount = 4
        }.hookReplace { param ->
            val runnable = param.args.getOrNull(2) as? Runnable
                ?: return@hookReplace param.invokeOriginal()
            requireNotNull(param.thisObject).callMethod("run", runnable)
        }
    }

    private fun disableNewVersionDialog() {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_20)) {
            "com.tencent.mobileqq.upgrade.ui.dialog.UpgradeActivity"
        } else {
            "com.tencent.mobileqq.upgrade.activity.UpgradeActivity"
        }.toClass.findMethod {
            name = "doOnCreate"
            paramTypes = arrayOf(bundle)
        }.hookReplace { param ->
            (requireNotNull(param.thisObject) as Activity).finish()
            true
        }

        "com.tencent.biz.qui.noticebar.view.VQUINoticeBarLayout".toClass
            .getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
            .hookAfter { param ->
                val view = requireNotNull(param.thisObject) as FrameLayout
                view.visibility = View.GONE
                view.layoutParams = FrameLayout.LayoutParams(0, 0)
            }
    }

    private fun disableGrayCheckDialog() {
        "com.tencent.mobileqq.graycheck.business.GrayCheckHandler".toClass
            .declaredMethods.firstOrNull {
                it.isPublic && it.returnType == Void.TYPE &&
                        it.paramCount == 1 && it.parameterTypes[0] == FromServiceMsg::class.java
            }?.hookBefore { it.result = Unit }
    }

    private fun disableFekitDialog() {
        "com.tencent.mobileqq.dt.api.impl.DTAPIImpl".toClass
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
}
