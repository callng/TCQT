package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class RestoreMessageBadgePosition : IAction, DexKitTask {

    override val key: String get() = "restore_message_badge_position"
    override val name: String get() = "还原消息红点气泡位置"
    override val desc: String get() = "将会话列表的未读消息红点气泡移动到消息区域右下角，并隐藏多余的免打扰图标。"
    override val uiTab: String get() = "界面"

    override fun onInit(): Boolean {
        return HookEnv.isQQ()
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "getSummary" to FindMethod().apply {
            matcher {
                declaredClass = RECENT_ITEM_LAYOUT
                returnType = "android.view.View"
                usingEqStrings("summary")
            }
        },
        "getSummaryRightView" to FindMethod().apply {
            matcher {
                declaredClass = RECENT_ITEM_LAYOUT
                returnType = "android.widget.ImageView"
                usingEqStrings("summaryRightView")
            }
        },
        "getRightLayout" to FindMethod().apply {
            matcher {
                declaredClass = RECENT_ITEM_LAYOUT
                returnType = "android.widget.RelativeLayout"
                usingEqStrings("rightLayout")
            }
        },
        "updateDisturbIcon" to FindMethod().apply {
            searchPackages("com.tencent.qqnt.chats.main.ui.processor")
            matcher {
                paramCount = 3
                paramTypes(null, ROLLING_TEXT_VIEW, "android.widget.ImageView")
                usingEqStrings("item", "view", "summaryRightView", "icon_tertiary")
            }
        }
    )

    override fun onRun(app: Application, process: ActionProcess) {
        val itemLayoutClass = RECENT_ITEM_LAYOUT.toClass
        val rootLayoutClass = SWIPE_MENU_LAYOUT.toClass
        val rollingTextViewClass = ROLLING_TEXT_VIEW.toClass

        val createLayout = itemLayoutClass.findMethod {
            paramTypes = arrayOf(context)
            returnType = rootLayoutClass
        }
        val getBadge = itemLayoutClass.findMethod {
            paramCount = 0
            returnType = rollingTextViewClass
        }
        val getSummary = requireMethod("getSummary")
        val getSummaryRightView = requireMethod("getSummaryRightView")
        val getRightLayout = requireMethod("getRightLayout")

        createLayout.hookAfter { param ->
            val itemLayout = param.thisObject
            val badge = getBadge.invoke(itemLayout) as? View ?: return@hookAfter
            val messageArea = getRightLayout.invoke(itemLayout) as? RelativeLayout ?: return@hookAfter
            val summaryRightView = getSummaryRightView.invoke(itemLayout) as? ImageView ?: return@hookAfter
            val summary = getSummary.invoke(itemLayout) as? View ?: return@hookAfter
            val summaryRightLayoutParams = summaryRightView.layoutParams as RelativeLayout.LayoutParams

            if (badge.parent !== messageArea) {
                (badge.parent as? ViewGroup)?.removeView(badge)
                messageArea.addView(
                    badge,
                    createMessageAreaLayoutParams(summaryRightLayoutParams)
                )
            } else {
                badge.layoutParams = createMessageAreaLayoutParams(summaryRightLayoutParams)
            }

            (summary.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                removeRule(RelativeLayout.START_OF)
                addRule(RelativeLayout.START_OF, badge.id)
                summary.layoutParams = this
            }

            messageArea.clipChildren = false
            messageArea.clipToPadding = false
        }

        // 隐藏免打扰图标
        requireMethod("updateDisturbIcon").hookAfter { param ->
            (param.args[2] as ImageView).visibility = View.GONE
        }
    }

    private fun createMessageAreaLayoutParams(
        summaryRightLayoutParams: RelativeLayout.LayoutParams
    ) =
        RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(
                RelativeLayout.BELOW,
                summaryRightLayoutParams.getRule(RelativeLayout.BELOW)
            )
            addRule(RelativeLayout.ALIGN_PARENT_END)
            marginEnd = summaryRightLayoutParams.marginEnd
        }

    private companion object {
        const val RECENT_ITEM_LAYOUT = "com.tencent.qqnt.chats.kit.x2k.ChatRecentContactItemLayout"
        const val SWIPE_MENU_LAYOUT = "com.tencent.qqnt.widget.SwipeMenuLayout"
        const val ROLLING_TEXT_VIEW = "com.tencent.qqnt.chats.view.RollingTextView"
    }
}
