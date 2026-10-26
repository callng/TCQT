package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import org.json.JSONObject
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

/**
 * 代码来自 QAuxiliary 的 MiniAppToStruckMsg，改成只走 NT 路径。
 *
 * 卡片的跳转链接只存在于 ark 的 json 里，用户能看到的却是"打开小程序"的卡片，
 * 复制不了。这里在消息列表创建 item 之前把 ark 元素换成纯文本，链接就回到
 * 普通文本消息的待遇（可选中、可点击、可复制）。
 */
@RegisterAction
object MiniAppRecvToLink : Feature(
    key = "mini_app_recv_to_link",
    name = "小程序分享转链接（接收）",
    desc = "把聊天里的小程序分享卡片显示成标题加原始链接的文本。消息项构建时改写，"
            + "卡片多的会话滑动可能掉帧。",
), DexKitTask {

    override fun install() {
        val method = requireMethod(METHOD_CREATE_MSG_ITEM)
        val recordIndex = method.parameterTypes.indexOfFirst { it == MsgRecord::class.java }
        if (recordIndex < 0) {
            Log.w("MiniAppRecvToLink: 目标方法没有 MsgRecord 参数，功能不生效")
            return
        }

        method.hookBefore { param ->
            val record = param.args.getOrNull(recordIndex) as? MsgRecord ?: return@hookBefore
            val elements = record.elements ?: return@hookBefore
            val arkJson = elements.firstNotNullOfOrNull { it.arkElement?.bytesData } ?: return@hookBefore
            val text = arkJson.toLinkText() ?: return@hookBefore

            elements.clear()
            elements.add(
                MsgElement().apply {
                    // 宿主按 elementType 决定从哪个字段取内容，不设就渲染成空白气泡
                    elementType = ELEMENT_TYPE_TEXT
                    setTextElement(TextElement().apply { content = text })
                }
            )
            // 渲染哪套 item builder 完全由 msgType 决定，不同步改掉就还是卡片
            record.msgType = MSG_TYPE_TEXT
            record.subMsgType = 0
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        METHOD_CREATE_MSG_ITEM to FindMethod().apply {
            matcher {
                usingStrings("[createMsgItemByType] err. msgType=", "MsgListUtil")
            }
        }
    )

    /** 只认 QQ 小程序（`detail_1.qqdocurl`）和微信小程序（`miniapp.jumpUrl`）两种卡片。 */
    private fun String.toLinkText(): String? {
        val root = runCatching { JSONObject(this) }.getOrNull() ?: return null
        if (!root.optString("app").contains(APP_MINI_PROGRAM, ignoreCase = true)) return null

        val meta = root.optJSONObject("meta") ?: return null
        val qqDocUrl = meta.optJSONObject("detail_1")
            ?.let { it.optString("qqdocurl").ifBlank { it.optString("url") } }
            .orEmpty()
        val wxJumpUrl = meta.optJSONObject("miniapp")?.optString("jumpUrl").orEmpty()
        val url = qqDocUrl.ifBlank { wxJumpUrl }
        // mqqapi:// 只在小程序容器里能打开，转成文本反而变成死链
        if (url.isBlank() || url.startsWith(MQQAPI_SCHEME)) return null

        val prompt = root.optString("prompt").ifBlank { "小程序分享" }
        return "$prompt\n$url"
    }

    /** QQ 文本消息的 msgType，和 KMSGTYPEMIX 同值。 */
    private const val MSG_TYPE_TEXT = 2

    /** 元素类型：文本。宿主渲染正文时靠它选字段。 */
    private const val ELEMENT_TYPE_TEXT = 1
    private const val METHOD_CREATE_MSG_ITEM = "createMsgItem"
    private const val APP_MINI_PROGRAM = "com.tencent.miniapp"
    private const val MQQAPI_SCHEME = "mqqapi://"
}
