package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.helper.AntiRecallConfig
import com.owo233.tcqt.hooks.helper.OnAIOViewUpdate
import com.owo233.tcqt.hooks.helper.RecallManager
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.new
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.util.WeakHashMap
import androidx.core.graphics.toColorInt

class RecallHeaderTip : IAction, OnAIOViewUpdate {

    override val key: String get() = "msg_anti_recall"
    override val name: String get() = "消息防撤回-顶部提醒"
    override val hidden: Boolean get() = true

    private data class MessageLayout(
        val isGroup: Boolean,
        val isSelf: Boolean
    )

    private val boundMessages = WeakHashMap<ViewGroup, Set<RecallManager.MessageKey>>()
    private val boundLayouts = WeakHashMap<ViewGroup, MessageLayout>()
    private var listenerRegistered = false

    override fun canRun(): Boolean =
        TCQTSetting.getBoolean(key) && AntiRecallConfig.isTopTipEnabled()

    override fun onInit(): Boolean {
        if (!listenerRegistered) {
            RecallManager.addListener(::onMessageRecalled)
            listenerRegistered = true
        }
        return super.onInit()
    }

    override fun onRun(app: Application, process: ActionProcess) = Unit

    override fun onGetViewNt(
        view: ViewGroup,
        msgRecord: MsgRecord,
        param: MethodHookParam
    ) {
        if (!canRun()) {
            boundMessages.remove(view)
            boundLayouts.remove(view)
            render(view, recalled = false)
            return
        }

        val keys = RecallManager.keysOf(msgRecord)
        boundMessages[view] = keys
        boundLayouts[view] = MessageLayout(
            isGroup = msgRecord.chatType == MsgConstant.KCHATTYPEGROUP,
            isSelf = isSelfMessage(msgRecord)
        )
        render(view, RecallManager.isMessageRecalled(keys))
    }

    private fun onMessageRecalled(key: RecallManager.MessageKey) {
        SyncUtils.runOnUiThread {
            boundMessages.entries
                .filter { (_, keys) -> key in keys }
                .map { it.key }
                .forEach { view -> render(view, recalled = true) }
        }
    }

    private fun render(view: ViewGroup, recalled: Boolean) {
        val bubbleView = findBubbleView(view)

        if (bubbleView == null) {
            view.findViewById<View>(ID_RECALL_TIP)?.visibility = View.GONE
            return
        }

        if (!recalled) {
            view.findViewById<View>(ID_RECALL_TIP)?.visibility = View.GONE
            return
        }

        val tip = view.findViewById(ID_RECALL_TIP)
            ?: createAndAttachTip(view, bubbleView, boundLayouts[view])
        tip.visibility = View.VISIBLE
    }

    private fun isSelfMessage(msgRecord: MsgRecord): Boolean {
        if (msgRecord.sendType == MsgConstant.KSENDTYPESELF) return true
        return runCatching {
            msgRecord.senderUin == QQInterfaces.currentUin.toLong()
        }.getOrDefault(false)
    }

    private fun findBubbleView(view: ViewGroup): View? = view.children.firstOrNull { child ->
        child is LinearLayout && child.id != View.NO_ID
    }

    private fun createAndAttachTip(
        view: ViewGroup,
        bubbleView: View,
        messageLayout: MessageLayout?
    ): TextView {
        return buildTip(view.context).also { tip ->
            view.addView(tip, 0)
            applyConstraints(view, bubbleView, messageLayout)
        }
    }

    private fun buildTip(context: Context): TextView {
        val accent = "#D94A4A".toColorInt()
        return TextView(context).apply {
            id = ID_RECALL_TIP
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "↓ 已撤回"
            textSize = 9.5f
            includeFontPadding = false
            setTextColor(accent)
            setPadding(context.dp(5f), context.dp(2f), context.dp(5f), context.dp(2f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(6f).toFloat()
                setColor(
                    Color.argb(
                        30,
                        Color.red(accent),
                        Color.green(accent),
                        Color.blue(accent)
                    )
                )
            }
        }
    }

    private fun applyConstraints(
        view: ViewGroup,
        bubbleView: View,
        messageLayout: MessageLayout?
    ) {
        val cs = constraintSetClz.new() ?: return
        cs.invoke("clone", view)

        val params = bubbleView.layoutParams as? ConstraintLayout.LayoutParams
        val horizontalSide = if (messageLayout?.isGroup == true && messageLayout.isSelf) {
            ConstraintSet.END
        } else {
            ConstraintSet.START
        }

        cs.invoke(
            "connect",
            ID_RECALL_TIP, horizontalSide,
            bubbleView.id, horizontalSide, 0
        )
        cs.invoke(
            "setMargin",
            ID_RECALL_TIP, horizontalSide,
            view.context.dp(BUBBLE_CONTENT_INSET_DP)
        )

        val originalTopMargin = params?.topMargin?.coerceAtLeast(0) ?: 0
        val groupTopSpacing = if (messageLayout?.isGroup == true) {
            view.context.dp(GROUP_VERTICAL_SPACING_DP)
        } else {
            0
        }
        val insertedIntoTopChain = when {
            params != null && params.topToBottom != ConstraintSet.UNSET -> {
                cs.invoke(
                    "connect",
                    ID_RECALL_TIP, ConstraintSet.TOP,
                    params.topToBottom, ConstraintSet.BOTTOM,
                    originalTopMargin + groupTopSpacing
                )
                true
            }

            params != null && params.topToTop != ConstraintSet.UNSET -> {
                cs.invoke(
                    "connect",
                    ID_RECALL_TIP, ConstraintSet.TOP,
                    params.topToTop, ConstraintSet.TOP,
                    originalTopMargin + groupTopSpacing
                )
                true
            }

            else -> false
        }

        if (insertedIntoTopChain) {
            cs.invoke("clear", bubbleView.id, ConstraintSet.TOP)
            cs.invoke(
                "connect",
                bubbleView.id, ConstraintSet.TOP,
                ID_RECALL_TIP, ConstraintSet.BOTTOM,
                view.context.dp(
                    if (messageLayout?.isGroup == true) GROUP_VERTICAL_SPACING_DP else 2f
                )
            )
            cs.invoke("setGoneMargin", bubbleView.id, ConstraintSet.TOP, 0)
        } else {
            cs.invoke(
                "connect",
                ID_RECALL_TIP, ConstraintSet.BOTTOM,
                bubbleView.id, ConstraintSet.TOP, view.context.dp(2f)
            )
        }

        cs.invoke("applyTo", view)
    }

    companion object {
        private const val ID_RECALL_TIP = 0x114520
        private const val BUBBLE_CONTENT_INSET_DP = 8f
        private const val GROUP_VERTICAL_SPACING_DP = 4f

        private val constraintSetClz by lazy {
            "androidx.constraintlayout.widget.ConstraintSet".toHostClass()
        }
    }
}

private fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()
