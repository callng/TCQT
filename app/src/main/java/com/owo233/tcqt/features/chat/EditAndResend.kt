package com.owo233.tcqt.features.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitLookupTracker
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.features.internal.pipeline.OnMenuBuilder
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.ContactHelper
import com.owo233.tcqt.host.service.CustomMenu
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.ref.WeakReference
import androidx.core.view.isVisible

/**
 * 编辑重发。
 *
 * 在消息长按菜单里新增「编辑重发」：把该条文本消息的内容填回输入框，
 * 同时撤回原消息，用户改完直接发送即可。
 *
 * 复刻自 QAuxiliary 的 `me/hd/hook/menu/EditTextContent.kt`。
 */
@RegisterAction
object EditAndResend : Feature(
    key = "edit_and_resend",
    name = "编辑重发",
    desc = "长按菜单添加编辑功能",
    priority = ActionPriority.EARLY,
), DexKitTask, OnMenuBuilder {

    /** 排在 PttForward(100)、RepeatMessage(200) 之后。 */
    override val decoratorOrder: Int = 250

    /** 只挂在文本消息组件上（其他类型没有可编辑的文本）。 */
    override val targetComponentTypes: Array<String>
        get() = arrayOf(
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
        )

    /** 输入框引用。宿主重建输入框时会被覆盖，用弱引用避免泄漏。 */
    @Volatile
    private var editTextRef: WeakReference<EditText>? = null

    /**
     * 必须自己声明 DexKit 查询。
     *
     * 只实现 [DexKitTask] 而不重写查询的话，`getCacheKeys()` 默认返回空集，
     * `DexKitFinder` 就不会去查找这个 key，`requireMethod` 必然失败 ——
     * 表现就是「菜单项能出现，但点了一直提示找不到输入框」。
     * （`GetSign` 也用了同一个 key，但它默认关闭，所以指望不上。）
     */
    override fun getCacheKeys(): Set<String> = setOf(TASK_INPUT_ROOT_INIT)

    override fun execute(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
        tracker: DexKitLookupTracker,
    ) {
        lookup(TASK_INPUT_ROOT_INIT, bridge, cache, tracker) {
            findMethod(
                FindMethod().apply {
                    searchPackages("com.tencent.mobileqq.aio.input.simpleui")
                    matcher {
                        usingEqStrings(
                            "binding",
                            "inputRoot",
                            "findViewById(...)",
                            "getContext(...)",
                            "sendBtn",
                        )
                    }
                },
            ).singleOrNull()?.descriptor
        }
    }

    override fun install() {
        // hook 输入框初始化，反射找 EditText 字段 —— 不依赖字段名（混淆了也找得到）。
        requireMethod(TASK_INPUT_ROOT_INIT).hookAfter { param ->
            val et = runCatching {
                param.thisObject.javaClass
                    .findField { type == EditText::class.java }
                    .get(param.thisObject) as? EditText
            }.getOrNull() ?: return@hookAfter
            editTextRef = WeakReference(et)
        }
    }

    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        val record = runCatching { MsgRecordHelper.get(msg) }.getOrNull() ?: return

        // 只对自己发的消息显示（sendType == 0 是对方发的）
        if (record.sendType == 0) return

        // 超过撤回时限就不显示 —— 否则撤不掉，用户会白白多发一条
        if (!canRecall(record)) return

        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "编辑重发",
            icon = R.drawable.ic_item_edit_72dp,
            id = R.id.item_edit_to_send,
        ) {
            onEditAndResend(record)
        }

        val menuList = param.result as? List<*> ?: return
        param.result = listOf(item) + menuList
    }

    private fun onEditAndResend(record: MsgRecord) {
        val text = buildString {
            runCatching {
                record.elements?.forEach { element ->
                    element.textElement?.content?.let { append(it) }
                }
            }
        }

        if (text.isEmpty()) {
            Toasts.error("该消息没有可编辑的文本")
            return
        }

        val editText = findInputEditText()
        if (editText == null) {
            Toasts.error("未找到输入框，请先点开聊天输入框再试")
            return
        }

        // setText 必须在主线程
        Handler(Looper.getMainLooper()).post {
            runCatching {
                editText.setText(text)
                editText.setSelection(editText.text?.length ?: text.length)
                // 让输入框拿到焦点并主动弹出软键盘
                editText.requestFocus()
                editText.performClick()
                editText.postDelayed({
                    runCatching {
                        val imm = editText.context
                            .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, 120L)
            }.onFailure { Log.e("edit_and_resend: 写入输入框失败", it) }
        }

        recall(record)
    }

    /**
     * 消息是否还在撤回时限内。
     *
     * QQ 普通消息的撤回时限是 2 分钟，超时后撤回必然失败 ——
     * 那时就不该提供「编辑重发」，否则用户会白白多发一条。
     * `MsgRecord.msgTime` 是秒级时间戳。
     */
    private fun canRecall(record: MsgRecord): Boolean {
        val msgTime = runCatching { record.msgTime }.getOrDefault(0L)
        if (msgTime <= 0L) return true // 拿不到时间就不拦，交给撤回结果兜底
        val now = System.currentTimeMillis() / 1000L
        return now - msgTime <= RECALL_LIMIT_SECONDS
    }

    private fun recall(record: MsgRecord) {
        val contact = ContactHelper.generateContactByUid(record.chatType, record.peerUid)
        if (contact !is MapleContact.PublicContact) {
            Log.e("edit_and_resend: 宿主版本过低，无法生成 PublicContact（需 QQ 9.0.70+）")
            return
        }

        runCatching {
            QQInterfaces.msgService
                .recallMsg(contact.inner, arrayListOf(record.msgId)) { code, err ->
                    if (code == 0) {
                        Toasts.success("已撤回")
                    } else {
                        Toasts.error("撤回失败：$err")
                    }
                }
        }.onFailure { Log.e("edit_and_resend: 撤回异常", it) }
    }

    /**
     * 拿聊天输入框。
     *
     * 优先用 hook 缓存的引用；拿不到就从当前界面 View 树里找。
     * 需要兜底的原因：`InputRootInit` 只在输入框初始化时被调用一次，
     * 如果模块安装晚于这个时机，hook 之后就永远不会再触发。
     */
    @SuppressLint("DiscouragedApi")
    private fun findInputEditText(): EditText? {
        // ① 优先用 hook 缓存的引用
        editTextRef?.get()?.let { cached ->
            if (cached.isAttachedToWindow) {
                return cached
            }
        }

        val activity = runCatching { QQInterfaces.topActivity }.getOrNull()
        val root = runCatching { activity?.window?.decorView }.getOrNull()
        if (root == null) {
            Log.e("edit_and_resend: decorView 为空")
            return null
        }

        // ② 精确找聊天输入框：它一定和「发送按钮」在同一个输入栏容器里。
        //    只按 is Shown 之类找的话，第一个命中往往是搜索框 QuickPinyinEditText。
        val res = root.resources
        val pkg = runCatching { QQInterfaces.topActivity.packageName }.getOrNull() ?: "com.tencent.mobileqq"
        val sendBtnId = runCatching { res.getIdentifier("send_btn", "id", pkg) }.getOrDefault(0)
        if (sendBtnId == 0) return null

        val sendBtn = runCatching { root.findViewById<View>(sendBtnId) }.getOrNull()
        if (sendBtn == null) return null

        // 从发送按钮往上爬几层，在每一层里找 EditText（输入栏通常就在附近）
        var parent: Any? = sendBtn.parent
        var depth = 0
        while (parent is ViewGroup && depth < 6) {
            val et = findEditTextIn(parent)
            if (et != null) {
                return et
            }
            parent = parent.parent
            depth++
        }

        Log.e("edit_and_resend: 发送按钮附近没找到 EditText")
        return null
    }

    private fun findEditTextIn(view: View?): EditText? {
        if (view == null) return null
        if (view is EditText) {
            return view.takeIf { it.isVisible && it.isEnabled }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEditTextIn(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** 从 AIOMsgItem 反射拿 MsgRecord（组件上的方法名是固定的 getMsgRecord）。 */
    private object MsgRecordHelper {

        private val getMsgRecordMethod by lazy {
            loadOrThrow("com.tencent.mobileqq.aio.msg.AIOMsgItem")
                .getDeclaredMethod("getMsgRecord")
                .apply { isAccessible = true }
        }

        fun get(msgItem: Any): MsgRecord = getMsgRecordMethod.invoke(msgItem) as MsgRecord
    }

    /** QQ 普通消息的撤回时限（秒）。 */
    private const val RECALL_LIMIT_SECONDS = 120L

    /** DexKit 查询 key。与 GetSign 用同一个 key，`tracker` 会做同名去重。 */
    private const val TASK_INPUT_ROOT_INIT = "InputRootInit"
}
