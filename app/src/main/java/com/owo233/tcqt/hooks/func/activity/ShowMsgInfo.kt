package com.owo233.tcqt.hooks.func.activity

import android.annotation.SuppressLint
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
import androidx.core.view.isVisible
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.HookEnv.requireMinTimVersion
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.shortClassName
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.hooks.helper.OnAIOViewUpdate
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.ui.MsgDetailDialog
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.TIMVersion
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.new
import com.owo233.tcqt.utils.reflect.toJsonString
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RegisterAction
class ShowMsgInfo : IAction, OnAIOViewUpdate {

    override val key get() = "show_msg_info"
    override val name get() = "显示消息Seq与时间"
    override val desc get() =  $$"支持占位符: ${msgSeq} (消息Seq), ${formatTime} (发送时间)"
    override val uiTab get() = "界面"
    override val settings get() = listOf(
        StringSetting(
            key = "show_msg_info.format",
            name = "显示格式",
            defaultValue = $$"${msgSeq} ${formatTime}",
            desc = "如果为空则使用默认格式",
            placeholder = $$"自定义格式, e.g. ${msgSeq} [${formatTime}]"
        )
    )

    private val timeFormatter by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    private val placeholderProviders = mapOf<String, (MsgRecord) -> String>(
        "msgSeq" to { it.msgSeq.toString() },
        "formatTime" to { timeFormatter.format(Date(it.msgTime * 1000L)) }
    )

    private fun formatMessage(msg: MsgRecord, template: String): String {
        var result = template
        placeholderProviders.forEach { (name, provider) ->
            result = result.replace($$"${$$name}", provider(msg))
        }
        return result
    }

    override fun onRun(app: Application, process: ActionProcess) = Unit

    override fun onGetViewNt(
        view: ViewGroup,
        msgRecord: MsgRecord,
        param: MethodHookParam
    ) {
        if (!isVersionSupported()) return

        val infoLayout = view.findViewById(ID_INFO_LAYOUT)
            ?: createAndAttachInfoLayout(view, msgRecord)

        bindMessageInfo(infoLayout, msgRecord)
    }

    // ── View Creation ────────────────────────────────────────────────

    private fun createAndAttachInfoLayout(
        view: ViewGroup,
        msg: MsgRecord
    ): LinearLayout {
        return buildInfoLayout(view.context).also {
            view.addView(it)
            applyConstraints(view, msg)
        }
    }

    private fun buildInfoLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            id = ID_INFO_LAYOUT
            layoutParams = ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = 5f
                alpha = 0x22
            }
            setPadding(context.dp(4f), context.dp(1f), context.dp(4f), context.dp(1f))
            addView(buildInfoTextView(context))
        }
    }

    private fun buildInfoTextView(context: Context): TextView {
        return TextView(context).apply {
            id = ID_INFO_TEXT
            setTextColor(Color.WHITE)
            textSize = 9.5f
            setOnClickListener { v ->
                (v.tag as? MsgRecord)?.let { msg ->
                    MsgDetailDialog(
                        context,
                        msg.shortClassName,
                        msg.toJsonString(maxDepth = 5, prettyPrint = true)
                    ).show()
                }
            }
        }
    }

    // ── Constraints ──────────────────────────────────────────────────

    private fun applyConstraints(view: ViewGroup, msg: MsgRecord) {
        val cs = constraintSetClz.new() ?: return
        cs.invoke("clone", view)

        val bubbleView = view.children.firstOrNull {
            it is LinearLayout && it.id != View.NO_ID
        } ?: return

        val verticalAnchor = view.children.lastOrNull { child ->
            child.id != View.NO_ID &&
                    child.id != ID_INFO_LAYOUT &&
                    child.isVisible
        } ?: return

        cs.invoke(
            "connect",
            ID_INFO_LAYOUT, ConstraintSet.TOP,
            verticalAnchor.id, ConstraintSet.BOTTOM, 0
        )

        val isSelf = if (msg.chatType == 8) {
            msg.sendType == 1
        } else {
            msg.senderUin == QQInterfaces.currentUin.toLong()
        }
        val side = if (isSelf) ConstraintSet.END else ConstraintSet.START

        cs.invoke(
            "connect",
            ID_INFO_LAYOUT, side,
            bubbleView.id, side
        )

        cs.invoke(
            "setMargin",
            ID_INFO_LAYOUT, side,
            view.context.dp(8f)
        )

        cs.invoke("applyTo", view)
    }

    // ── Data Binding ─────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun bindMessageInfo(layout: LinearLayout, msg: MsgRecord) {
        layout.findViewById<TextView>(ID_INFO_TEXT).apply {
            tag = msg
            val template = TCQTSetting.getString("show_msg_info.format").ifBlank { $$"${msgSeq} ${formatTime}" }
            text = formatMessage(msg, template)
        }
        layout.visibility = View.VISIBLE
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun isVersionSupported(): Boolean =
        requireMinQQVersion(QQVersion.QQ_8_9_63_BETA_11345) ||
                requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)

    companion object {
        private const val ID_INFO_LAYOUT = 0x114510
        private const val ID_INFO_TEXT = 0x114511

        private val constraintSetClz by lazy {
            "androidx.constraintlayout.widget.ConstraintSet".toHostClass()
        }
    }
}

private fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()
