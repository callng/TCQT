package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.helper.OnAIOViewUpdate
import com.owo233.tcqt.utils.MethodHookParam
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.new
import com.tencent.qqnt.aio.widget.AIOMsgTextView
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
@RegisterSetting(
    key = "ait_chameleon",
    name = "艾特变色龙",
    type = SettingType.BOOLEAN,
    desc = "赋予群聊中 @艾特文本 点击功能，可直接跳转至该成员的群名片资料页。",
    uiTab = "界面"
)
class AitChameleon : IAction, OnAIOViewUpdate {

    private var TextView.msgTag: MsgRecord?
        get() = getTag(TAG_KEY_MSG) as? MsgRecord
        set(value) = setTag(TAG_KEY_MSG, value)

    private var TextView.isInjecting: Boolean
        get() = getTag(TAG_KEY_GUARD) == true
        set(value) = setTag(TAG_KEY_GUARD, value)

    companion object {
        private const val TAG_KEY_MSG = -0x7E00_0001
        private const val TAG_KEY_GUARD = -0x7E00_0002
        private const val TAG_KEY_BG_CACHE = -0x7E00_0003
    }

    override val key: String get() = GeneratedSettingList.AIT_CHAMELEON

    override fun onRun(ctx: Context, process: ActionProcess) = Unit

    override fun onGetViewNt(rootView: ViewGroup, chatMessage: MsgRecord, param: MethodHookParam) {
        rootView.allChildViews
            .filter { it.javaClass == AIOMsgTextView::class.java }
            .mapNotNull { it as? TextView }
            .forEach { tv ->
                tv.msgTag = chatMessage
                tv.post { injectRealAtIfNeeded(tv) }
            }
    }

    private fun injectRealAtIfNeeded(tv: TextView) {
        if (tv.isInjecting) return

        val msgRecord = tv.msgTag ?: return
        val raw = tv.text?.takeIf { it.isNotEmpty() } ?: return
        val atList = msgRecord.extractAllMentions().takeIf { it.isNotEmpty() } ?: return

        val sp = (raw as? Spannable ?: SpannableStringBuilder(raw)).apply {
            if (getSpans(0, length, RealAtSpan::class.java).isNotEmpty()) return
        }

        val full = sp.toString()
        val occupied = sp.getSpans(0, sp.length, ClickableSpan::class.java)
            .map { sp.getSpanStart(it) to sp.getSpanEnd(it) }

        val bgColor = tv.cachedBgColor()
        val originTextColor = tv.currentTextColor

        var cursor = 0
        var injected = 0

        atList.forEach { at ->
            val start = full.indexOf(at.display, cursor).takeIf { it >= 0 } ?: return@forEach
            val end = start + at.display.length
            cursor = end

            if (occupied.any { it.first < end && start < it.second }) return@forEach

            sp.setSpan(
                RealAtSpan(
                    peerId = at.peerId,
                    uid = at.uid,
                    ntUid = at.ntUid,
                    display = at.display,
                    originalTextColor = originTextColor,
                    bgColor = bgColor,
                    click = ::onRealMentionClick
                ),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            injected++
        }

        if (injected > 0) tv.applyStyle(sp)
    }

    private fun TextView.cachedBgColor(): Int {
        val cached = getTag(TAG_KEY_BG_CACHE) as? Int
        if (cached != null) return cached
        val v = resolveEffectiveBackgroundColor()
        setTag(TAG_KEY_BG_CACHE, v)
        return v
    }

    private fun MsgRecord.extractAllMentions(): List<AtInfo> {
        val currentMentions = elements?.mapNotNull { el ->
            el.textElement
                ?.takeIf { it.atType == 2 && it.content?.startsWith("@") == true }
                ?.let {
                    AtInfo(
                        display = it.content,
                        uid = it.atUid,
                        ntUid = it.atNtUid ?: "",
                        peerId = peerUid
                    )
                }
        } ?: emptyList()

        val referencedMentions = records?.flatMap { record ->
            record.extractAllMentions()
        } ?: emptyList()

        return currentMentions + referencedMentions
    }

    private fun onRealMentionClick(view: View, peerId: String, uid: Long, ntUid: String, display: String) {
        val context = view.context

        runCatching {
            val allInOne = "com.tencent.mobileqq.profilecard.data.AllInOne"
                .toHostClass()
                .new(uid.toString(), 20)

            FieldUtils.create(allInOne).apply {
                named("uid").setValue(ntUid)
                named("troopUin").setValue(peerId)
                named("troopCode").setValue(peerId)
                named("profileEntryType").setValue(1)
                named("subSourceId").setValue(11)
                named("extras").setValue(Bundle().apply {
                    putInt("enter_page_sourceid", 1)
                    putInt("enter_page_subsourceid", 11)
                })
            }

            val intent = Intent().apply {
                setClassName(
                    context,
                    if (HookEnv.isQQ())
                        "com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity"
                    else
                        "com.tencent.mobileqq.profilecard.activity.TimFriendProfileCardActivity"
                )
                putExtra("memberUin", uid.toString())
                putExtra("troopUin", peerId)
                putExtra("AllInOne", allInOne as Parcelable)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.e("Jump to profile card failed", it)
            Toasts.error("跳转群名片失败")
        }
    }

    private fun TextView.applyStyle(sp: Spannable) {
        if (movementMethod !is LinkMovementMethod) {
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0
        }
        isInjecting = true
        try {
            text = sp
        } finally {
            isInjecting = false
        }
    }

    private fun TextView.resolveEffectiveBackgroundColor(): Int {
        var v: View? = this
        while (v != null) {
            val bg = v.background
            if (bg is android.graphics.drawable.ColorDrawable) {
                val c = bg.color
                if (Color.alpha(c) > 0) return c
            }
            v = (v.parent as? View)
        }

        val a = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        return try {
            val d = a.getDrawable(0)
            if (d is android.graphics.drawable.ColorDrawable) d.color else Color.WHITE
        } finally {
            a.recycle()
        }
    }

    private val ViewGroup.allChildViews: Sequence<View>
        get() = sequence {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                yield(child)
                if (child is ViewGroup) yieldAll(child.allChildViews)
            }
        }

    private data class AtInfo(
        val display: String,
        val uid: Long,
        val ntUid: String,
        val peerId: String
    )

    private class RealAtSpan(
        val peerId: String,
        val uid: Long,
        val ntUid: String,
        val display: String,
        val originalTextColor: Int,
        val bgColor: Int,
        val click: (View, String, Long, String, String) -> Unit
    ) : ClickableSpan() {

        override fun onClick(widget: View) = click(widget, peerId, uid, ntUid, display)

        override fun updateDrawState(ds: android.text.TextPaint) {
            ds.isUnderlineText = false
            ds.color = originalTextColor
            ds.isFakeBoldText = true

            val bgLum = ColorUtils.calculateLuminance(bgColor)
            val shadowBase = if (bgLum > 0.6) Color.BLACK else Color.WHITE

            val alpha = if (bgLum > 0.6) 0.22f else 0.34f
            val radius = if (bgLum > 0.6) 1.6f else 2.4f
            val dy = if (bgLum > 0.6) 0.4f else 0.8f

            val shadowColor = ColorUtils.setAlphaComponent(
                shadowBase,
                (alpha * 255f).toInt().coerceIn(0, 255)
            )

            ds.setShadowLayer(radius, 0f, dy, shadowColor)
        }
    }
}
