package com.owo233.tcqt.hooks.func.activity

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.annotation.SuppressLint
import android.app.Application
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.CustomMenu
import com.owo233.tcqt.hooks.helper.OnMenuBuilder
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.callMethod
import com.owo233.tcqt.utils.reflect.getMethods
import com.owo233.tcqt.utils.reflect.new
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class EditResendTextMessage : IAction, OnMenuBuilder {

    override val name: String get() = "快捷编辑重发消息"
    override val desc: String get() = "长按自己发送的文本消息显示编辑重发按钮，将原文填入输入框并撤回原消息。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "edit_resend_text_message"

    override val targetComponentTypes: Array<String>
        get() = arrayOf(TEXT_COMPONENT, MIX_COMPONENT)

    override fun onRun(app: Application, process: ActionProcess) = Unit

    @SuppressLint("SetTextI18n")
    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        val msgRecord = msg.callMethod("getMsgRecord") as? MsgRecord ?: return
        if (msgRecord.sendType == 0) return

        val text = msgRecord.elements.joinToString(separator = "") { element ->
            element.textElement?.content.orEmpty()
        }
        if (text.isBlank()) return

        val component = param.thisObject
        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "编辑重发",
            icon = R.drawable.ic_item_edit_72dp,
            id = R.id.item_edit_to_send,
            click = {
                if (sendTextToInput(component, text)) {
                    recallMsg(msgRecord)
                } else {
                    Toasts.error("输入框未就绪")
                }
            }
        )

        @Suppress("UNCHECKED_CAST")
        (param.result as? MutableList<Any>)?.add(0, item)
    }

    private fun sendTextToInput(component: Any, text: String): Boolean {
        val intent = runCatching {
            loadOrThrow(INPUT_SET_TEXT_INTENT).new(text, true)
        }.getOrElse { e ->
            Log.e("edit resend failed: create SetTextToEditText intent", e)
            return false
        }

        val method = component.javaClass.getMethods(true).firstOrNull { method ->
            method.name == "sendIntent" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(intent.javaClass)
        } ?: run {
            Log.e("edit resend failed: sendIntent method not found")
            return false
        }

        return runCatching {
            method.invoke(component, intent)
            true
        }.getOrElse { e ->
            Log.e("edit resend failed: dispatch input intent", e)
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

    private companion object {
        const val TEXT_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent"
        const val MIX_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent"
        const val INPUT_SET_TEXT_INTENT =
            "com.tencent.mobileqq.aio.input.edit.InputEditTextMsgIntent\$SetTextToEditText"
    }
}
