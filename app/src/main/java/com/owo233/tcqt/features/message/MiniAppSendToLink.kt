package com.owo233.tcqt.features.message

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.hook.hookMethodAfter

/**
 * 代码来自 QAuxiliary 的 NoApplet（"远离小程序"），保留 NT 下可用的两条分支。
 *
 * 小程序点分享时 QQ 只是打开 JumpActivity 处理一个 mqqapi scheme，卡片内容全靠
 * scheme 里的 base64 参数。在这里把 scheme 换成纯文本分享，发出去的就是链接本身。
 */
@RegisterAction
object MiniAppSendToLink : Feature(
    key = "mini_app_send_to_link",
    name = "小程序分享转链接（发送）",
    desc = "在小程序里分享到 QQ 时，把分享卡片换成标题加原始链接的文字消息。",
    // 分享冷启动时 JumpActivity 的 onCreate 紧跟着 Application.onCreate，装晚了就漏
    priority = ActionPriority.EARLY,
) {

    override fun install() {
        Activity::class.java.hookMethodAfter("getIntent") { param ->
            // 所有 Activity 的 getIntent 都会走到这里，先按类名挡掉绝大部分
            if (param.thisObject.javaClass.simpleName != JUMP_ACTIVITY) return@hookMethodAfter
            val origin = param.result as? Intent ?: return@hookMethodAfter
            val data = origin.data ?: return@hookMethodAfter

            when {
                data.toString().contains(SCHEME_SHARE_TO_FRIEND) ->
                    asTextShare(origin, data)?.let { param.result = it }

                data.toString().contains(SCHEME_MINI_PROGRAM) ->
                    param.result = stripMiniProgram(origin, data)
            }
        }
    }

    private fun asTextShare(origin: Intent, data: Uri): Intent? {
        val url = data.shareParam("url")?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: return null

        val text = buildString {
            data.shareParam("title")?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            data.shareParam("description")?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            append(url)
        }

        return Intent(origin).apply {
            // 留着原 scheme 会被 JumpActivity 继续当卡片处理，换成标准文本分享
            this.data = null
            component = null
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun stripMiniProgram(origin: Intent, data: Uri): Intent {
        val stripped = data.toString()
            .replace(MINI_PROGRAM_NODE_REGEX, "")
            .replace(REQ_TYPE_REGEX, "req_type=MQ==")
        return origin.apply {
            this.data = stripped.toUri()
            component = null
        }
    }

    /** 分享参数是 base64；解不出来（明文分享）就原样返回。 */
    private fun Uri.shareParam(key: String): String? {
        val raw = getQueryParameter(key)?.takeIf { it.isNotEmpty() } ?: return null
        val decoded = runCatching {
            String(Base64.decode(raw.replace(' ', '+'), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
        return decoded?.takeIf { it.isNotBlank() } ?: raw
    }

    private const val JUMP_ACTIVITY = "JumpActivity"
    private const val SCHEME_SHARE_TO_FRIEND = "mqqapi://share/to_fri"
    private const val SCHEME_MINI_PROGRAM = "mini_program"
    private const val MINI_PROGRAM_NODE_REGEX = "(mini_program.*?)=([^&]*)&"
    private const val REQ_TYPE_REGEX = "(req_type=[^&]*)"
}
