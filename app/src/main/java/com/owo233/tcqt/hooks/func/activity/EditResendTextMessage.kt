package com.owo233.tcqt.hooks.func.activity

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.annotation.SuppressLint
import android.app.Application
import android.widget.EditText
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.CustomMenu
import com.owo233.tcqt.hooks.helper.OnMenuBuilder
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class EditResendTextMessage : IAction, DexKitTask, OnMenuBuilder {

    override val name: String get() = "快捷编辑重发消息"
    override val desc: String get() = "长按自己发送的文本消息显示编辑重发按钮，将原文填入输入框并撤回原消息。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "edit_resend_text_message"

    override fun onRun(app: Application, process: ActionProcess) {
        val method = if (HookEnv.isTim()) {
            "com.tencent.tim.aio.inputbar.simpleui.TimAIOInputSimpleUIVBDelegate".toClass.findMethod {
                name = "B"
            }
        } else {
            requireMethod(INPUT_ROOT_INIT)
        }

        method.hookAfter { param ->
            val editText = runCatching {
                param.thisObject::class.java.findField { type = EditText::class.java }
                    .get(param.thisObject) as? EditText
            }.getOrNull() ?: return@hookAfter

            inputEditText = editText
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        val msgRecord = MsgRecordHelper.getMsgRecord(msg)
        if (msgRecord.sendType == 0) return
        val text = msgRecord.getTextContent()
        if (text.isBlank()) return

        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "编辑重发",
            icon = R.drawable.ic_item_edit_72dp,
            id = R.id.item_edit_to_send,
            click = click@{
                if (setTextToInput(text)) {
                    recallMsg(msgRecord)
                } else {
                    Toasts.error("输入框未就绪")
                }
            }
        )

        @Suppress("UNCHECKED_CAST")
        (param.result as? MutableList<Any>)?.add(0, item)
    }

    private fun MsgRecord.getTextContent(): String {
        return elements.joinToString(separator = "") { element ->
            element.textElement?.content.orEmpty()
        }
    }

    private fun setTextToInput(text: String): Boolean {
        val editText = inputEditText ?: return false
        return runCatching {
            editText.setText(text)
            editText.setSelection(text.length)
            true
        }.getOrElse { e ->
            Log.e("edit resend failed: set input text", e)
            false
        }
    }

    private fun recallMsg(msgRecord: MsgRecord) {
        val contact = ContactHelper.generateContactByUid(msgRecord.chatType, msgRecord.peerUid)
        if (contact !is MapleContact.PublicContact) {
            Log.e("edit resend failed: Host version too low, need 9.0.70+")
            return
        }

        QQInterfaces.msgService.recallMsg(contact.inner, arrayListOf(msgRecord.msgId)) { code, reason ->
            if (code == 0) {
                Toasts.success("撤回成功")
            } else {
                Toasts.error("撤回失败: $reason")
            }
        }
    }

    private object MsgRecordHelper {

        private val getMsgRecordMethod by lazy {
            loadOrThrow("com.tencent.mobileqq.aio.msg.AIOMsgItem")
                .getDeclaredMethod("getMsgRecord")
                .apply { isAccessible = true }
        }

        fun getMsgRecord(msgItem: Any): MsgRecord {
            return getMsgRecordMethod.invoke(msgItem) as MsgRecord
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        INPUT_ROOT_INIT to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.input")
            matcher {
                usingEqStrings(
                    "binding",
                    "inputRoot",
                    "findViewById(...)",
                    "getContext(...)",
                    "sendBtn"
                )
            }
        }
    )

    private companion object {
        @Volatile
        private var inputEditText: EditText? = null

        const val INPUT_ROOT_INIT = "InputRootInit"
    }
}
