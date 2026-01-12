package com.owo233.tcqt.hooks.func.activity

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.OnAIOViewUpdate
import com.owo233.tcqt.utils.context.HostContextFactory
import com.owo233.tcqt.utils.reflect.toJsonString
import com.tencent.mobileqq.vas.theme.api.ThemeUtil
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

    override fun onRun(ctx: Context, process: ActionProcess) {
        // 无需任何实现
    }

    override val key: String get() = GeneratedSettingList.SHOW_MSG_INFO

    @SuppressLint("SetTextI18n")
    override fun onUpdate(frameLayout: FrameLayout, msgRecord: MsgRecord) {
        val timeView = frameLayout.getOrCreateTimeView()

        val timeString = timeFormatter.format(Date(msgRecord.msgTime * 1000L))
        val infoText = "Seq: ${msgRecord.msgSeq} Time: $timeString"

        if (timeView.text != infoText) {
            timeView.text = infoText
        }

        timeView.setOnClickListener {
            showDetailDialog(it.context, msgRecord)
        }
    }

    private fun showDetailDialog(context: Context, msgRecord: MsgRecord) {
        val newContext = HostContextFactory.createMaterialContext(context)
        val detail = msgRecord.toJsonString()

        val textView = TextView(newContext).apply {
            text = detail
            textSize = 14f
            setPadding(20.dp, 10.dp, 20.dp, 10.dp)
            setTextIsSelectable(true)
            setTextColor(0xFF333333.toInt())
        }

        val scrollView = ScrollView(newContext).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            addView(textView)
        }

        val dialog = AlertDialog.Builder(newContext)
            .setTitle("消息详情")
            .setView(scrollView)
            .setPositiveButton("复制") { _, _ -> context.copyToClipboard(detail, true) }
            .setNeutralButton("确定", null)
            .create()

        dialog.show()
    }

    private fun FrameLayout.getOrCreateTimeView(): TextView {
        return findViewWithTag<TextView>(VIEW_TAG) ?: TextView(context).apply {
            setTextColor(if (isDarkMode()) 0xBBBBBBBB.toInt() else 0x88444444.toInt())

            tag = VIEW_TAG
            textSize = 11f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.BOTTOM
            }

            this@getOrCreateTimeView.addView(this)
        }
    }

    private fun isDarkMode(): Boolean {
        return ThemeUtil.isNowThemeIsNight(null, true, null)
                || (if (HookEnv.isQQ()) ThemeUtil.isThemeNightModeV2() else false)
    }

    private val Int.dp: Int
        get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    companion object {
        private const val VIEW_TAG = "tcqt_msg_time_info"
    }
}
