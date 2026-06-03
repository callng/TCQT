package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.CustomMenu
import com.owo233.tcqt.hooks.helper.OnMenuBuilder
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.getFields
import com.owo233.tcqt.utils.reflect.getMethods
import com.owo233.tcqt.utils.reflect.new
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

@RegisterAction
class EditResendTextMessage : IAction, OnMenuBuilder {

    override val name: String get() = "快捷编辑重发消息"
    override val desc: String get() = "长按自己发送的文本消息显示重新编辑按钮。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "edit_resend_text_message"

    override val targetComponentTypes: Array<String>
        get() = REEDIT_MENU_COMPONENTS

    override fun onRun(app: Application, process: ActionProcess) {
        runCatching {
            hookRevokeGrayTipReedit()
        }.onFailure { e ->
            Log.e("edit resend failed: hook official gray tip re-edit", e)
        }
    }

    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        runCatching {
            addReeditMenuItem(msg, param)
        }.onFailure { e ->
            Log.e("edit resend failed: build menu item", e)
        }
    }

    private fun addReeditMenuItem(msg: Any, param: MethodHookParam) {
        val msgRecord = MsgRecordHelper.getMsgRecord(msg)
        if (!msgRecord.canReeditByOfficialRule()) return

        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "重新编辑",
            icon = R.drawable.ic_item_edit_72dp,
            id = R.id.item_edit_to_send,
            click = click@{
                if (!quickReeditByOfficialFlow(param.thisObject, msg, msgRecord)) {
                    Log.e("edit resend failed: run official re-edit flow")
                }
            }
        )

        @Suppress("UNCHECKED_CAST")
        (param.result as? MutableList<Any>)?.add(0, item)
    }

    private fun MsgRecord.canReeditByOfficialRule(): Boolean {
        val currentUid = QQInterfaces.currentUid
        val serverTimeSeconds = QQInterfaces.getServiceTime() / 1000
        return currentUid.isNotEmpty() &&
            currentUid == senderUid &&
            editable &&
            msgTime + REEDIT_TIME_LIMIT_SECONDS > serverTimeSeconds
    }

    private fun hookRevokeGrayTipReedit() {
        val componentClass = loadOrThrow(REVOKE_GRAY_TIPS_COMPONENT)
        componentClass.getDeclaredMethod(
            "K1",
            Int::class.javaPrimitiveType,
            loadOrThrow(AIO_DATA_MSG_ITEM),
            List::class.java
        ).hookAfter { param ->
            val grayTipsMsgItem = param.args.getOrNull(1) ?: return@hookAfter
            triggerPendingReedit(param.thisObject, grayTipsMsgItem)
        }
    }

    private fun quickReeditByOfficialFlow(component: Any, msg: Any, msgRecord: MsgRecord): Boolean {
        val eventBus = component.getOfficialEventBus() ?: return false
        cleanupPendingReedit()
        pendingReedits[msgRecord.msgId] = PendingReedit.from(msgRecord)

        return runCatching {
            eventBus.publishOfficialEvent(loadOrThrow(REVOKE_CHECK_EVENT).new(msg, true, 0, null))
            true
        }.getOrElse { e ->
            pendingReedits.remove(msgRecord.msgId)
            Log.e("edit resend failed: publish official revoke event", e)
            false
        }
    }

    private fun triggerPendingReedit(component: Any, grayTipsMsgItem: Any) {
        runCatching {
            triggerPendingReeditSafely(component, grayTipsMsgItem)
        }.onFailure { e ->
            Log.e("edit resend failed: trigger official gray tip re-edit", e)
        }
    }

    private fun triggerPendingReeditSafely(component: Any, grayTipsMsgItem: Any) {
        if (!loadOrThrow(GRAY_TIPS_MSG_ITEM).isInstance(grayTipsMsgItem)) return

        val msgRecord = MsgRecordHelper.getMsgRecord(grayTipsMsgItem)
        val pending = pendingReedits[msgRecord.msgId]
            ?: pendingReedits.values.firstOrNull { it.matches(msgRecord) }
            ?: return
        if (!grayTipsMsgItem.invokeBoolean("K2")) return

        pendingReedits.remove(pending.msgId)
        runCatching {
            val intent = loadOrThrow(REEDIT_GRAY_TIP_INTENT).new(grayTipsMsgItem)
            component.invokeOneArg("sendIntent", intent)
            component.invokeNoArg("h2")
        }.onFailure { e ->
            Log.e("edit resend failed: trigger official gray tip re-edit", e)
        }
    }

    private fun cleanupPendingReedit() {
        val now = System.currentTimeMillis()
        pendingReedits.entries.removeIf { now - it.value.createdAt > PENDING_REEDIT_TIMEOUT_MS }
    }

    private fun Any.getOfficialEventBus(): Any? {
        return runCatching {
            val runtime = getFields(true)
                .firstOrNull { it.type.name == AIO_RUNTIME }
                ?.apply { isAccessible = true }
                ?.get(this)
                ?: return null
            runtime.invokeNoArg("e")
        }.getOrElse { e ->
            Log.e("edit resend failed: get official event bus", e)
            null
        }
    }

    private fun Any.invokeNoArg(name: String): Any {
        val method = javaClass.getMethods(true).first { method ->
            method.name == name && method.parameterTypes.isEmpty()
        }
        method.isAccessible = true
        return method.invoke(this)
    }

    private fun Any.invokeOneArg(name: String, arg: Any): Any? {
        val method = javaClass.getMethods(true).first { method ->
            method.name == name &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isAssignableFrom(arg.javaClass)
        }
        method.isAccessible = true
        return method.invoke(this, arg)
    }

    private fun Any.invokeBoolean(name: String): Boolean {
        return runCatching { invokeNoArg(name) as? Boolean }.getOrNull() == true
    }

    private fun Any.publishOfficialEvent(event: Any) {
        val method: Method = javaClass.getMethods(true).first { method ->
            method.name == "h" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isAssignableFrom(event.javaClass)
        }
        method.isAccessible = true
        method.invoke(this, event)
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

    private data class PendingReedit(
        val msgId: Long,
        val msgSeq: Long,
        val msgTime: Long,
        val peerUid: String,
        val senderUid: String,
        val createdAt: Long
    ) {
        fun matches(msgRecord: MsgRecord): Boolean {
            return peerUid == msgRecord.peerUid &&
                senderUid == msgRecord.senderUid &&
                (msgSeq == msgRecord.msgSeq || msgTime == msgRecord.msgTime)
        }

        companion object {
            fun from(msgRecord: MsgRecord): PendingReedit {
                return PendingReedit(
                    msgId = msgRecord.msgId,
                    msgSeq = msgRecord.msgSeq,
                    msgTime = msgRecord.msgTime,
                    peerUid = msgRecord.peerUid,
                    senderUid = msgRecord.senderUid,
                    createdAt = System.currentTimeMillis()
                )
            }
        }
    }

    private companion object {
        val pendingReedits = ConcurrentHashMap<Long, PendingReedit>()
        const val AIO_RUNTIME = "com.tencent.aio.api.runtime.a"
        const val AIO_DATA_MSG_ITEM = "com.tencent.aio.data.msglist.a"
        const val GRAY_TIPS_MSG_ITEM = "com.tencent.mobileqq.aio.msg.GrayTipsMsgItem"
        const val REEDIT_GRAY_TIP_INTENT = "com.tencent.mobileqq.aio.msglist.holder.component.graptips.b\$a"
        const val REVOKE_CHECK_EVENT = "com.tencent.qqnt.aio.menu.MenuMsgEvent\$RevokeCheck"
        const val REVOKE_GRAY_TIPS_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.graptips.revoke.RevokeGrayTipsComponent"
        const val PENDING_REEDIT_TIMEOUT_MS = 30_000L
        const val REEDIT_TIME_LIMIT_SECONDS = 120L
        const val TEXT_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent"
        const val REPLY_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.reply.AIOReplyComponent"
        const val MIX_COMPONENT = "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent"
        val REEDIT_MENU_COMPONENTS = arrayOf(
            TEXT_COMPONENT,
            REPLY_COMPONENT,
            MIX_COMPONENT
        )
    }
}
