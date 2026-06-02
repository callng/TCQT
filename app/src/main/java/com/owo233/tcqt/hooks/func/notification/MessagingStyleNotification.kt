package com.owo233.tcqt.hooks.func.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.LogUtils

@RegisterAction
class MessagingStyleNotification : IAction {

    override val name: String get() = "MessagingStyle 通知"
    override val desc: String get() = "更加优雅的通知样式，致敬 QQ Helper。"
    override val uiTab: String get() = "通知"
    override val key: String get() = "messaging_style_notification"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                OPTIONS_KEY,
                "可选项",
                0,
                "",
                listOf("禁用子渠道发送通知", "禁用通知气泡", "自动清除多余通知子渠道"),
                forcedSelections = mapOf(OPTION_AUTO_CLEAR_SUB_CHANNEL to listOf(OPTION_DISABLE_SUB_CHANNEL))
            )
        )

    private val notificationCapture = MessagingNotificationCapture()
    private val notificationBuilder = MessagingNotificationBuilder(
        disableConversationSubChannel = { disableConversationSubChannel },
        disableBubble = { disableBubble }
    )
    private val logger = LogUtils.xposedNoFilter

    private val options: Int
        get() = TCQTSetting.getInt(OPTIONS_KEY)

    private val disableConversationSubChannel: Boolean
        get() = options.isFlagEnabled(OPTION_DISABLE_SUB_CHANNEL) || autoClearConversationSubChannel

    private val disableBubble: Boolean
        get() = options.isFlagEnabled(OPTION_DISABLE_BUBBLE)

    private val autoClearConversationSubChannel: Boolean
        get() = options.isFlagEnabled(OPTION_AUTO_CLEAR_SUB_CHANNEL)

    override fun onRun(app: Application, process: ActionProcess) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!createNotificationChannels(app)) return

        val notificationFacade = load("com.tencent.qqnt.notification.NotificationFacade")
            ?: return skip("NotificationFacade not found")
        val appRuntimeClass = load("mqq.app.AppRuntime")
            ?: return skip("AppRuntime not found")
        val commonInfoClass = load("com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo")
            ?: return skip("NotificationCommonInfo not found")
        val recentInfoClass = load("com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo")
            ?: return skip("RecentContactInfo not found")
        val postTarget = findPostNotificationMethod(notificationFacade)
            ?: return skip("post notification method not found")

        val buildPathHooked = notificationCapture.hookBuildPaths(
            notificationFacade,
            appRuntimeClass,
            commonInfoClass,
            recentInfoClass
        )
        if (!buildPathHooked) {
            return skip("notification build path not found")
        }

        postTarget.first.hookBefore { param ->
            val oldNotification = param.args[postTarget.second] as? Notification ?: return@hookBefore
            val pair = notificationCapture.take(oldNotification) ?: return@hookBefore
            val newNotification = runCatching {
                notificationBuilder.createNotification(pair.first, pair.second, oldNotification)
            }.onFailure {
                logger.w("MessagingStyleNotification replace failed", it)
            }.getOrNull() ?: return@hookBefore

            param.args[postTarget.second] = newNotification
        }

        postTarget.first.hookAfter {
            if (autoClearConversationSubChannel) {
                notificationBuilder.clearRedundantConversationChannels()
            }
        }

        load("com.tencent.commonsdk.util.notification.QQNotificationManager")
            ?.declaredMethods
            ?.firstOrNull { it.name == "cancelAll" && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.hookBefore {
                notificationBuilder.clearHistory()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannels(app: Application): Boolean {
        return runCatching {
            val notificationManager = app.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup("qq_evolution", "QQ通知进化 Plus")
            )
            createQAuxNotificationChannels(notificationManager)
        }.onFailure {
            logger.w("MessagingStyleNotification create channels failed", it)
        }.isSuccess
    }

    private fun skip(reason: String) {
        logger.w("MessagingStyleNotification skipped: $reason")
    }

    companion object {
        private const val OPTIONS_KEY = "messaging_style_notification.options"
        private const val OPTION_DISABLE_SUB_CHANNEL = 0
        private const val OPTION_DISABLE_BUBBLE = 1
        private const val OPTION_AUTO_CLEAR_SUB_CHANNEL = 2
    }
}
