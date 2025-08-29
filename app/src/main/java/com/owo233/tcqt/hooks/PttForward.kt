package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.CustomMenu
import com.owo233.tcqt.R
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.utils.ContextUtils
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import de.robv.android.xposed.XC_MethodHook
import java.io.File

@RegisterAction
class PttForward: AlwaysRunAction(), OnMenuBuilder {
    override fun onRun(ctx: Context, process: ActionProcess) {

    }

    override val name: String get() = "语音转发"

    override val targetComponentTypes: Array<String> get() = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent"
    )

    override fun onGetMenuNt(msg: Any, componentType: String, param: XC_MethodHook.MethodHookParam) {
        val context: Activity = ContextUtils.getCurrentActivity() ?: error("getCurrentActivity null")
        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "转发",
            icon = R.drawable.ic_item_share_72dp,
            id = R.id.item_ptt_forward,
            click = {
                // Toasts.success(context, "你点击了转发按钮")
                startForwardActivity(context, getPttFileByMsgNt(msg).path)
            }
        )

        @Suppress("UNCHECKED_CAST")
        val list = param.result as MutableList<Any>
        list.add(item)
    }

    private fun startForwardActivity(context: Context, str: String) {
        val intent = Intent(
            context,
            XpClassLoader.load("com.tencent.mobileqq.activity.ForwardRecentActivity")
        )
        intent.putExtra("selection_mode", 0)
        intent.putExtra("direct_send_if_dataline_forward", false)
        intent.putExtra("forward_text", "null")
        intent.putExtra("ptt_forward_path", str)
        intent.putExtra("forward_type", -1)
        intent.putExtra("caller_name", "ChatActivity")
        intent.putExtra("k_smartdevice", false)
        intent.putExtra("k_dataline", false)
        intent.putExtra("k_forward_title", "语音转发")
        context.startActivity(intent)
    }

    private fun getPttFileByMsgNt(msg: Any): File {
        return try {
            val getElement = msg.javaClass.declaredMethods
                .firstOrNull { it.returnType == PttElement::class.java }

            val element = getElement?.invoke(msg) as? PttElement
                ?: throw IllegalStateException("PttElement method not found or returned null")

            val filePath = element.filePath
            File(filePath)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get PttElement,$e")
        }
    }
}
