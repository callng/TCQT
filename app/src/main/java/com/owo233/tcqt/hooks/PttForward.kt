package com.owo233.tcqt.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.utils.CustomMenu
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.utils.ContextUtils
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.selectmember.ResultRecord
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.msg.api.IMsgService
import de.robv.android.xposed.XC_MethodHook
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.io.File
import java.lang.reflect.Method
import java.util.ArrayList
import kotlin.random.Random

@RegisterAction
@RegisterSetting(
    key = "ptt_forward",
    name = "允许转发语音消息",
    type = SettingType.BOOLEAN,
    desc = "长按语音消息显示转发按钮，可以将语音消息转发给其他好友或群。",
    uiOrder = 20
)
class PttForward : IAction, OnMenuBuilder {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onRun(ctx: Context, process: ActionProcess) {
        val forwardBaseOption = XpClassLoader.load(
            "com.tencent.mobileqq.forward.ForwardBaseOption"
        ) ?: error("ForwardBaseOption class not found")

        val mExtraDataField = forwardBaseOption.getDeclaredField("mExtraData")
            .apply { isAccessible = true }
            ?: error("mExtraData field not found")

        val methods = getMethods(
            forwardBaseOption,
            listOf(
                "isNeedShowToast" to arrayOf(
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                ),
                "getMultiTargetWithoutDataLine" to emptyArray()
            )
        )

        methods.forEach { method ->
            method.hookMethod(beforeHook(51) { param ->
                val thisObj = param.thisObject
                val data = mExtraDataField.get(thisObj) as? Bundle ?: return@beforeHook

                if (data.getString("ptt_forward") != "114514" ||
                    (!data.containsKey("isBack2Root") && !data.containsKey("from_dataline_aio")) ||
                    ptt == null) {
                    return@beforeHook
                }

                val msgService = QRoute.api(IMsgService::class.java)

                GlobalScope.launchWithCatch {
                    try {
                        data.getString("Uid")?.let { uid ->
                            val uinType = data.getInt("uintype", -1)
                            sendPtt(uid, uinType, msgService)
                        }

                        // 兼容多选
                        if (data.containsKey("forward_multi_target")) {
                            @Suppress("DEPRECATION")
                            val mForwardTargets: ArrayList<ResultRecord>? = data.getParcelableArrayList("forward_multi_target")
                            mForwardTargets?.forEach { t ->
                                sendPtt(t.uin, t.uinType, msgService)
                            }
                        }
                    } finally {
                        ptt =  null
                    }
                }
            })
        }
    }

    private fun getMethods(clazz: Class<*>, methodInfos: List<Pair<String, Array<Class<*>>>>): List<Method> {
        return methodInfos.map { (name, params) ->
            clazz.getDeclaredMethod(name, *params).apply { isAccessible = true }
        }
    }

    private suspend fun sendPtt(uin: String, uinType: Int, msgService: IMsgService) {
        val sendUid = if (uinType == 0) { // 私聊类型
            if (!uin.startsWith("u_")) {
                ContactHelper.getUidByUinAsync(uin.toLong())
            } else uin
        } else uin

        val elem = MsgElement().apply {
            elementType = MsgConstant.KELEMTYPEPTT
            pttElement = ptt
        }

        val contact = ContactHelper.generateContactByUid(if (uinType == 0) 1 else 2, sendUid)
        val newMsgId = generateMsgUniSeq(if (uinType == 0) 1 else 2)

        if (contact is MapleContact.PublicContact) {
            msgService.sendMsgWithMsgId(
                contact.inner,
                newMsgId,
                arrayListOf(elem),
                null)
        }
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
        intent.putExtra("forward_from_jump", true)
        intent.putExtra("ptt_forward", "114514")
        intent.putExtra("forward_type", -1)
        intent.putExtra("caller_name", "ChatActivity")
        intent.putExtra("k_smartdevice", false)
        intent.putExtra("k_dataline", false)
        intent.putExtra("is_need_show_toast", true)
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

    override val key: String get() = GeneratedSettingList.PTT_FORWARD

    override val targetComponentTypes: Array<String> get() = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent"
    )

    @SuppressLint("DiscouragedApi")
    @Suppress("UNCHECKED_CAST")
    override fun onGetMenuNt(msg: Any, componentType: String, param: XC_MethodHook.MethodHookParam) {
        ptt = getPttElement(msg)

        val context: Activity = ContextUtils.getCurrentActivity() ?: error("getCurrentActivity null")
        val resId = context.resources.getIdentifier(
            "guild_title_share_btn_icon_white", "drawable", hostInfo.packageName
        )
        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "转发",
            icon = resId,
            id = R.id.item_ptt_forward,
            click = {
                startForwardActivity(context, getPttFileByMsgNt(msg).absolutePath)
            }
        )

        val list = param.result as MutableList<Any>
        list.add(item)
    }

    companion object {
        var ptt: PttElement? = null
    }
}
