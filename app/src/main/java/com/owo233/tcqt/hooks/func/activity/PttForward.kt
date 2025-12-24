package com.owo233.tcqt.hooks.func.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.launchWithCatch
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.CustomMenu
import com.owo233.tcqt.hooks.helper.OnMenuBuilder
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.MethodHookParam
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookMethod
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.selectmember.ResultRecord
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.msg.api.IMsgService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.io.File

@RegisterAction
@RegisterSetting(
    key = "ptt_forward",
    name = "允许转发语音消息",
    type = SettingType.BOOLEAN,
    desc = "长按语音消息显示转发按钮，可以将语音消息转发给其他好友或群。",
    uiTab = "界面"
)
class PttForward : IAction, OnMenuBuilder {

    private companion object {
        const val MAGIC_TOKEN = "114514" // 恶臭的 MAGIC_TOKEN
        const val KEY_PTT_FORWARD = "ptt_forward"
        const val CLS_FORWARD_BASE = "com.tencent.mobileqq.forward.ForwardBaseOption"
        const val CLS_FORWARD_ACTIVITY = "com.tencent.mobileqq.activity.ForwardRecentActivity"
        var currentPttElement: PttElement? = null
    }

    override val key: String get() = GeneratedSettingList.PTT_FORWARD

    override val targetComponentTypes: Array<String> get() = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent"
    )

    @OptIn(DelicateCoroutinesApi::class)
    override fun onRun(ctx: Context, process: ActionProcess) {
        val forwardBaseOption = load(CLS_FORWARD_BASE) ?: error("$CLS_FORWARD_BASE not found")

        val mExtraDataField = forwardBaseOption.getDeclaredField("mExtraData").apply { isAccessible = true }

        val methodsToHook = listOf(
            "isNeedShowToast" to arrayOf(Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType),
            "getMultiTargetWithoutDataLine" to emptyArray()
        )

        methodsToHook.forEach { (methodName, params) ->
            forwardBaseOption.getDeclaredMethod(methodName, *params).apply { isAccessible = true }
                .hookMethod(beforeHook(51) { param ->
                    val thisObj = param.thisObject
                    val data = mExtraDataField.get(thisObj) as? Bundle ?: return@beforeHook

                    if (data.getString(KEY_PTT_FORWARD) != MAGIC_TOKEN ||
                        (!data.containsKey("isBack2Root") && !data.containsKey("from_dataline_aio")) ||
                        currentPttElement == null
                    ) {
                        return@beforeHook
                    }

                    val msgService = QRoute.api(IMsgService::class.java)

                    GlobalScope.launchWithCatch {
                        try {
                            data.getString("Uid")?.let { uid ->
                                val uinType = data.getInt("uintype", -1)
                                sendPtt(uid, uinType, msgService)
                            }
                            if (data.containsKey("forward_multi_target")) {
                                @Suppress("DEPRECATION")
                                val mForwardTargets: ArrayList<ResultRecord>? = data.getParcelableArrayList("forward_multi_target")
                                mForwardTargets?.forEach { t ->
                                    sendPtt(t.uin, t.uinType, msgService)
                                }
                            }
                        } finally {
                            currentPttElement = null
                        }
                    }
                })
        }
    }

    private suspend fun sendPtt(uin: String, uinType: Int, msgService: IMsgService) {
        // uinType: 0好友,1群组,3000讨论组
        val sendUid = if (uinType == 0) { // 私聊类型
            if (!uin.startsWith("u_")) {
                ContactHelper.getUidByUinAsync(uin.toLong())
            } else uin
        } else uin

        val elem = MsgElement().apply {
            elementType = MsgConstant.KELEMTYPEPTT
            pttElement = currentPttElement!!.apply {
                voiceType = 2 // 普通语音消息
                voiceChangeType = 0 // 不是变声语音
                if (HookEnv.isQQ()) {
                    otherBusinessInfo.aiVoiceType = 0 // 不是AI语音消息
                }
            }
        }

        val contact = ContactHelper.generateContactByUid(if (uinType == 0) 1 else 2, sendUid)

        if (contact is MapleContact.PublicContact) {
            msgService.sendMsg(contact.inner, arrayListOf(elem)) {result, str ->
                if (result != 0) {
                    Log.e("PttForward: error -> (result = $result, str = $str)")
                }
            }
        }
    }

    private fun startForwardActivity(context: Context, path: String) {
        val intent = Intent(context, load(CLS_FORWARD_ACTIVITY)).apply {
            putExtra("selection_mode", 2)
            putExtra("direct_send_if_dataline_forward", false)
            putExtra("forward_text", path)
            putExtra("forward_type", -1)
            putExtra("forward_from_jump", true)
            putExtra(KEY_PTT_FORWARD, MAGIC_TOKEN)
            putExtra("caller_name", "ChatActivity")
            putExtra("k_smartdevice", false)
            putExtra("k_dataline", false)
            putExtra("is_need_show_toast", true)
            putExtra("k_forward_title", "语音转发")
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    private fun getPttElementFromMsg(msg: Any): PttElement {
        return runCatching {
            val getElementMethod = msg.javaClass.declaredMethods
                .firstOrNull { it.returnType == PttElement::class.java }
                ?: error("PttElement method not found")

            getElementMethod.invoke(msg) as? PttElement ?: error("Returned null PttElement")
        }.getOrElse { e ->
            throw IllegalStateException("Failed to get PttElement, $e")
        }
    }

    @SuppressLint("DiscouragedApi")
    @Suppress("UNCHECKED_CAST")
    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        val ptt = getPttElementFromMsg(msg)
        currentPttElement = ptt

        val context: Context = HookEnv.hostAppContext
        ResourcesUtils.injectResourcesToContext(context.resources)

        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "转发",
            icon = R.drawable.ic_item_share_72dp,
            id = R.id.item_ptt_forward,
            click = {
                startForwardActivity(context, File(ptt.filePath).absolutePath)
            }
        )

        (param.result as MutableList<Any>).add(item)
    }
}
