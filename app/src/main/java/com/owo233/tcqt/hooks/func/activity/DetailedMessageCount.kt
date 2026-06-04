package com.owo233.tcqt.hooks.func.activity

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Application
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.helper.UnreadBadgeHelper
import com.owo233.tcqt.utils.hook.hookAfter

@RegisterAction
class DetailedMessageCount : IAction {

    override val name: String get() = "详细消息数量"
    override val desc: String get() = "显示完整未读消息数量，不再将超过上限的数量折叠为 99+。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "detailed_message_count"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun onRun(app: Application, process: ActionProcess) {
        UnreadBadgeHelper.hookQuiBadgeExactCount(name)
        UnreadBadgeHelper.hookRedTouchExactCount(name)
        UnreadBadgeHelper.hookFrameControllerBadgeExactCount(name)
        hookMiniAppMenuBadge()
        hookMiniAioUnreadCount()
    }

    private fun hookMiniAppMenuBadge() {
        val clazz = load(MINI_CUSTOM_WIDGET_UTIL) ?: return
        val method = runCatching {
            clazz.getDeclaredMethod(
                "updateCustomNoteTxt",
                TextView::class.java,
                INT_TYPE
            )
                .apply { isAccessible = true }
        }.getOrNull() ?: return

        method.hookAfter { param ->
            val textView = param.args.getOrNull(0) as? TextView ?: return@hookAfter
            val count = param.args.getOrNull(1) as? Int ?: return@hookAfter
            UnreadBadgeHelper.showExactCount(textView, count)
        }
    }

    private fun hookMiniAioUnreadCount() {
        MINI_AIO_UNREAD_CLASSES.forEach { className ->
            UnreadBadgeHelper.hookUnreadTextViewCount(className, name)
        }
    }

    private companion object {
        val INT_TYPE = Int::class.javaPrimitiveType!!

        const val MINI_CUSTOM_WIDGET_UTIL = "com.tencent.qqmini.sdk.core.utils.CustomWidgetUtil"

        val MINI_AIO_UNREAD_CLASSES = arrayOf(
            "com.tencent.mobileqq.activity.miniaio.c",
            "com.tencent.mobileqq.activity.miniaio.d",
            "com.tencent.mobileqq.activity.miniaio.e",
            "com.tencent.mobileqq.activity.miniaio.f",
            "com.tencent.mobileqq.activity.miniaio.g",
            "com.tencent.mobileqq.activity.miniaio.h",
            "com.tencent.mobileqq.activity.miniaio.i"
        )
    }
}
