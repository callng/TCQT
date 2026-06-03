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
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log

@RegisterAction
class DetailedMessageCount : IAction {

    override val name: String get() = "详细消息数量"
    override val desc: String get() = "显示完整未读消息数量，不再将超过上限的数量折叠为 99+。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "detailed_message_count"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun onRun(app: Application, process: ActionProcess) {
        UnreadBadgeHelper.hookQuiBadgeExactCount(name)
        hookTotalMessageBadgeLimit()
        hookMiniAppMenuBadge()
        hookMiniAioUnreadCount()
    }

    private fun hookTotalMessageBadgeLimit() {
        val clazz = load(FRAME_CONTROLLER_INJECT_IMPL) ?: return
        val quiBadgeClass = load(QUI_BADGE_CLASS) ?: return

        val method = clazz.declaredMethods.firstOrNull { method ->
            val params = method.parameterTypes
            params.size == 5 &&
                params[0] == INT_TYPE &&
                params[1] == INT_TYPE &&
                params[2] == INT_TYPE &&
                params[3] == quiBadgeClass &&
                params[4] == String::class.java
        }?.apply { isAccessible = true } ?: return

        method.hookBefore { param ->
            if (param.args.size > 2) {
                param.args[2] = Int.MAX_VALUE
            }
        }
        Log.i("$name: 总消息数量上限 Hook 已启用")
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
        Log.i("$name: 小程序菜单未读数 Hook 已启用")
    }

    private fun hookMiniAioUnreadCount() {
        MINI_AIO_UNREAD_CLASSES.forEach { className ->
            UnreadBadgeHelper.hookUnreadTextViewCount(className, name)
        }
    }

    private companion object {
        val INT_TYPE = Int::class.javaPrimitiveType!!

        const val QUI_BADGE_CLASS = "com.tencent.mobileqq.quibadge.QUIBadge"
        const val FRAME_CONTROLLER_INJECT_IMPL =
            "com.tencent.mobileqq.activity.framebusiness.controllerinject.FrameControllerInjectImpl"
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
