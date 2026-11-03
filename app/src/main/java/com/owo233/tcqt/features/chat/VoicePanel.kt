package com.owo233.tcqt.features.chat

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.QQInterfaces

/**
 * 发送语音文件：长按输入框的语音按钮，选一个本地音频文件，作为语音消息发出去。
 *
 * 复刻自 fork 版本 TCQT 的语音面板功能。
 */
@RegisterAction
object VoicePanel : Feature(
    key = "voice_panel",
    name = "语音面板",
    desc = "长按语音按钮打开面板，可发送本地音频或在线语音",
    uiOrder = 90,
    processes = setOf(ActionProcess.MAIN),
) {

    private const val TAG = "VoicePanel"

    /** 宿主语音按钮在快捷栏里的 tag 标识。 */
    private const val VOICE_BUTTON_TAG = 1000

    /** 当前会话。 */
    data class Session(val chatType: Int, val peerUid: String)

    override fun install() {
        if (!HookEnv.isQQ()) return
        hookVoiceButton()
    }

    private fun hookVoiceButton() {
        val shortcutBar = "com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout".toHostClass()

        // 快捷栏布局构建完成后，找到语音按钮并挂上长按监听。
        shortcutBar.hookMethodAfter({
            paramTypes(int, string, null)
        }) { param ->
            val layout = param.thisObject as? LinearLayout ?: return@hookMethodAfter
            val voiceButton = findVoiceButton(layout) ?: return@hookMethodAfter
            voiceButton.setOnLongClickListener { view ->
                onVoiceButtonLongClick(view)
                true
            }
        }
    }

    private fun findVoiceButton(parent: LinearLayout): ImageView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ImageView && (child.tag as? Int) == VOICE_BUTTON_TAG) {
                return child
            }
        }
        return null
    }

    private fun onVoiceButtonLongClick(view: View) {
        val session = resolveCurrentSession()
        if (session == null) {
            Toasts.error("无法获取当前会话")
            return
        }
        VoicePanelDialog(view.context, session.chatType, session.peerUid).show()
    }

    private fun resolveCurrentSession(): Session? = runCatching {
        val activity = QQInterfaces.topActivity
        val intent = activity?.intent
        if (activity == null || intent == null) {
            Log.w("$TAG: topActivity 或 intent 为空 activity=$activity")
            return@runCatching null
        }
        val peerUid = intent.getStringExtra("key_peerId")
        val chatType = intent.getIntExtra("key_chat_type", 0)
        if (!peerUid.isNullOrBlank() && chatType > 0) {
            return@runCatching Session(chatType, peerUid)
        }
        val keys = intent.extras?.keySet()?.joinToString(",")
        Log.w(
            "$TAG: 未取到会话 activity=${activity.javaClass.name} " +
                "keys=[$keys] peerUid=$peerUid chatType=$chatType"
        )
        null
    }.getOrElse { e ->
        Log.e("$TAG: resolveCurrentSession 失败", e)
        null
    }
}
