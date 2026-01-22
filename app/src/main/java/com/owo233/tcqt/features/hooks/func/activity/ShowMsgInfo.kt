package com.owo233.tcqt.features.hooks.func.activity

import android.annotation.SuppressLint
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
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.foundation.extensions.shortClassName
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.helper.OnAIOViewUpdate
import com.owo233.tcqt.foundation.internal.QQInterfaces
import com.owo233.tcqt.foundation.ui.CustomDialog
import com.owo233.tcqt.foundation.utils.MethodHookParam
import com.owo233.tcqt.foundation.utils.QQVersion
import com.owo233.tcqt.foundation.utils.TIMVersion
import com.owo233.tcqt.foundation.utils.new
import com.owo233.tcqt.foundation.utils.reflect.invoke
import com.owo233.tcqt.foundation.utils.reflect.toJsonString
import com.owo233.tcqt.foundation.utils.toClass
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RegisterAction
@RegisterSetting(
    key = "show_msg_info",
    name = "显示消息Seq与时间",
    type = SettingType.BOOLEAN,
    desc = "在每条消息气泡下显示消息Seq和具体发送时间。",
    uiTab = "界面"
)
class ShowMsgInfo : IAction, OnAIOViewUpdate {

    private val timeFormatter by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    override fun onRun(ctx: Context, process: ActionProcess) = Unit

    override val key: String get() = GeneratedSettingList.SHOW_MSG_INFO

    override fun onGetViewNt(
        rootView: ViewGroup,
        chatMessage: MsgRecord,
        param: MethodHookParam
    ) {
        if (!isVersionSupported()) return

        val layout = ensureInfoLayout(rootView, chatMessage)
        bindMessageInfo(layout, chatMessage)
    }

    private fun ensureInfoLayout(
        rootView: ViewGroup,
        msg: MsgRecord
    ): LinearLayout {
        return rootView.findViewById(ID_ADD_LAYOUT)
            ?: buildInfoLayout(rootView).also {
                rootView.addView(it)
                applyConstraints(rootView, msg)
            }
    }

    private fun buildInfoLayout(rootView: ViewGroup): LinearLayout {
        val context = rootView.context
        return LinearLayout(context).apply {
            id = ID_ADD_LAYOUT
            layoutParams = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = 10f
                alpha = 0x22
            }
            val px4 = context.dp2px(4f)
            val px6 = context.dp2px(6f)
            setPadding(px6, px4, px6, px4)
            addView(buildInfoTextView(context))
        }
    }

    private fun buildInfoTextView(context: Context): TextView {
        return TextView(context).apply {
            id = ID_ADD_TEXTVIEW
            setTextColor(Color.WHITE)
            setOnClickListener {
                val record = it.tag as MsgRecord
                showDetailInfoDialog(
                    context,
                    record.shortClassName,
                    record.toJsonString()
                )
            }
        }
    }

    private fun applyConstraints(
        rootView: ViewGroup,
        msg: MsgRecord
    ) {
        val constraintSet = constraintSetClz.new()
        constraintSet.invoke("clone", rootView)

        val anchorIndex = rootView.children.indexOfFirst { it is LinearLayout && it.id != View.NO_ID }
        if (anchorIndex < 1) return

        val msgId = rootView.getChildAt(anchorIndex).id
        val nameId = rootView.getChildAt(anchorIndex - 1).id

        constraintSet.invoke(
            "connect",
            ID_ADD_LAYOUT,
            ConstraintLayout.LayoutParams.TOP,
            msgId,
            ConstraintLayout.LayoutParams.BOTTOM,
            0
        )

        val isSelf = msg.senderUin == QQInterfaces.currentUin.toLong()

        if (isSelf) {
            constraintSet.invoke("connect", ID_ADD_LAYOUT, ConstraintSet.RIGHT, nameId, ConstraintSet.RIGHT)
            if (msg.chatType == 1) {
                constraintSet.invoke("setMargin", ID_ADD_LAYOUT, ConstraintSet.END, rootView.context.dp2px(10f))
            }
        } else {
            constraintSet.invoke("connect", ID_ADD_LAYOUT, ConstraintSet.LEFT, nameId, ConstraintSet.LEFT)
            val margin = when (msg.chatType) {
                1 -> 10f
                2 if msg.msgType == MsgConstant.KELEMTYPEFILE -> 55f
                else -> 0f
            }
            if (margin > 0) {
                constraintSet.invoke(
                    "setMargin",
                    ID_ADD_LAYOUT,
                    ConstraintSet.START,
                    rootView.context.dp2px(margin)
                )
            }
        }

        constraintSet.invoke("applyTo", rootView)
    }

    @SuppressLint("SetTextI18n")
    private fun bindMessageInfo(layout: LinearLayout, msg: MsgRecord) {
        val textView = layout.findViewById<TextView>(ID_ADD_TEXTVIEW)
        textView.tag = msg

        val time = timeFormatter.format(Date(msg.msgTime * 1000L))
        textView.text = "${msg.msgSeq} $time"

        layout.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
    }

    private fun isVersionSupported(): Boolean {
        return HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_63_BETA_11345) ||
                HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)
    }

    private fun showDetailInfoDialog(context: Context, title: String, msg: String) {
        CustomDialog.create(context)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(true)
            .setMessageSelectable(true)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun Context.dp2px(dp: Float): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val ID_ADD_LAYOUT = 0x114510
        private const val ID_ADD_TEXTVIEW = 0x114511

        private val constraintSetClz by lazy {
            "androidx.constraintlayout.widget.ConstraintSet".toClass
        }
    }
}
