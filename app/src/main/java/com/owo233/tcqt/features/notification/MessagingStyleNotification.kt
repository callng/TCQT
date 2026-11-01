package com.owo233.tcqt.features.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Application
import android.app.Notification
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.LogUtils

@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MSF])
object MessagingStyleNotification : Feature(
    key = "messaging_style_notification",
    name = "MessagingStyle 通知",
    desc = "更加优雅的通知样式，致敬 QQ Helper。",
) {

    /** 派生 key = `messaging_style_notification.options`。 */
    private val options by multiIntOption(
        settingKey = "options",
        name = "可选项",
        defaultValue = 0,
        options = listOf("禁用子渠道发送通知", "禁用通知气泡", "自动清除多余通知子渠道"),
        forcedSelections = mapOf(
            OPTION_AUTO_CLEAR_SUB_CHANNEL to listOf(OPTION_DISABLE_SUB_CHANNEL),
        ),
    )

    private val notificationCapture = MessagingNotificationCapture()
    private val notificationBuilder = MessagingNotificationBuilder(
        disableConversationSubChannel = { disableConversationSubChannel },
        disableBubble = { disableBubble }
    )

    private val logger = LogUtils.android

    private val disableConversationSubChannel: Boolean
        get() = options.isFlagEnabled(OPTION_DISABLE_SUB_CHANNEL) || autoClearConversationSubChannel

    private val disableBubble: Boolean
        get() = options.isFlagEnabled(OPTION_DISABLE_BUBBLE)

    private val autoClearConversationSubChannel: Boolean
        get() = options.isFlagEnabled(OPTION_AUTO_CLEAR_SUB_CHANNEL)

    override fun install() {
        if (!createNotificationChannels(hostApp)) return

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

    private fun createNotificationChannels(app: Application): Boolean {
        return runCatching {
            val notificationManager = app.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup("qq_evolution", "TCQT 通知优化")
            )
            createQAuxNotificationChannels(notificationManager)
        }.onFailure {
            logger.w("MessagingStyleNotification create channels failed", it)
        }.isSuccess
    }

    private fun skip(reason: String) {
        logger.w("MessagingStyleNotification skipped: $reason")
    }

    private const val OPTION_DISABLE_SUB_CHANNEL = 0
    private const val OPTION_DISABLE_BUBBLE = 1
    private const val OPTION_AUTO_CLEAR_SUB_CHANNEL = 2
}
