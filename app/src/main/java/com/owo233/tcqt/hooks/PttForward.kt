package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.utils.CustomMenu
import com.owo233.tcqt.R
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.ContextUtils
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.msg.api.IMsgService
import de.robv.android.xposed.XC_MethodHook
import java.io.File
import kotlin.random.Random

@RegisterAction
class PttForward: IAction, OnMenuBuilder {
    override fun onRun(ctx: Context, process: ActionProcess) {
        val forwardBaseOption = XpClassLoader.load(
            "com.tencent.mobileqq.forward.ForwardBaseOption"
        ) ?: error("ForwardBaseOption class not found")

        val mExtraDataField = forwardBaseOption.getDeclaredField("mExtraData")
            .apply { isAccessible = true }
            ?: error("mExtraData field not found")

        val isNeedShowToast = forwardBaseOption.getDeclaredMethod(
            "isNeedShowToast",
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType
        ) ?: error("buildConfirmDialog method not found")

        isNeedShowToast.hookMethod(beforeHook(51) { param ->
            val thisObj = param.thisObject
            val data = mExtraDataField.get(thisObj) as? Bundle ?: return@beforeHook

            val uid = data.getString("Uid") ?: return@beforeHook
            val homo = data.getString("ptt_forward") ?: return@beforeHook
            val uinType = data.getInt("uintype", -1)

            if (homo != "114514" || !data.containsKey("isBack2Root") || ptt == null) return@beforeHook

            val elem = MsgElement()
            elem.elementType = MsgConstant.KELEMTYPEPTT
            elem.pttElement = ptt

            val contact = ContactHelper.generateContactByUid(if (uinType == 0) 1 else 2, uid)
            val newMsgId = generateMsgUniSeq(if (uinType == 0) 1 else 2)
            val msgService = QRoute.api(IMsgService::class.java)
            when (contact) {
                is MapleContact.PublicContact -> {
                    msgService.sendMsgWithMsgId(contact.inner,
                        newMsgId,
                        arrayListOf(elem),
                        null)
                }
                else -> {}
            }

            ptt = null
        })
    }

    private fun generateMsgUniSeq(chatType: Int): Long {
        val uniseq = (System.currentTimeMillis() / 1000) shl 32
        val random = Random.nextLong() and 0xffffff00L
        return uniseq or random or chatType.toLong()
    }

    private fun startForwardActivity(context: Context, path: String) {
        val intent = Intent(
            context,
            XpClassLoader.load("com.tencent.mobileqq.activity.ForwardRecentActivity")
        )
        intent.putExtra("selection_mode", 2)
        intent.putExtra("direct_send_if_dataline_forward", false)
        intent.putExtra("forward_text", path)
        intent.putExtra("forward_type", -1)
        intent.putExtra("ptt_forward", "114514")
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

    private fun getPttElement(msg: Any): PttElement {
        return try {
            val getElement = msg.javaClass.declaredMethods
                .firstOrNull { it.returnType == PttElement::class.java }

            val element = getElement?.invoke(msg) as? PttElement
                ?: throw IllegalStateException("PttElement method not found or returned null")

            element
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get PttElement,$e")
        }
    }

    override val name: String get() = "语音转发"

    override val key: String get() = TCQTSetting.PTT_FORWARD

    override val targetComponentTypes: Array<String> get() = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent"
    )

    override fun onGetMenuNt(msg: Any, componentType: String, param: XC_MethodHook.MethodHookParam) {
        ptt = getPttElement(msg)

        val context: Activity = ContextUtils.getCurrentActivity() ?: error("getCurrentActivity null")
        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "转发",
            icon = R.drawable.ic_item_share_72dp,
            id = R.id.item_ptt_forward,
            click = {
                startForwardActivity(context, getPttFileByMsgNt(msg).absolutePath)
            }
        )

        @Suppress("UNCHECKED_CAST")
        val list = param.result as MutableList<Any>
        list.add(item)
    }

    companion object {
        var ptt: PttElement? = null
    }
}
