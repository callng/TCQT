package com.owo233.tcqt.features.hooks.helper

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.AlwaysRunAction
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.features.hooks.func.activity.ShowMsgInfo
import com.owo233.tcqt.foundation.utils.MethodHookParam
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.owo233.tcqt.foundation.utils.isPublic
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.paramCount
import com.owo233.tcqt.foundation.utils.reflect.FieldUtils
import com.owo233.tcqt.foundation.utils.toClass
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msg.GrayTipsMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class AIOViewUpdate : AlwaysRunAction() {

    private val vbClass by lazy { "com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB".toClass }
    private val mviStateClass by lazy { "com.tencent.mvi.base.mvi.MviUIState".toClass }

    private val decorators: Array<OnAIOViewUpdate> = arrayOf(
        ShowMsgInfo()
    )

    override fun onRun(ctx: Context, process: ActionProcess) {
        val activeDecorators = decorators
            .filterIsInstance<IAction>()
            .filter { it.canRun() }
            .filterIsInstance<OnAIOViewUpdate>()
            .takeIf { it.isNotEmpty() } ?: return

        val targetMethod = vbClass.declaredMethods.singleOrNull { method ->
            method.isPublic &&
                    method.returnType == Void.TYPE &&
                    method.paramCount == 1 &&
                    method.parameterTypes[0] == mviStateClass
        } ?: error("Target method in AIOBubbleMsgItemVB not found!")

        targetMethod.hookAfterMethod { param ->
            runCatching {
                val host = param.thisObject ?: return@hookAfterMethod
                val uiState = param.args[0] ?: return@hookAfterMethod

                val rootView = FieldUtils.create(host)
                    .typed<View>()
                    .getOrNull() as? ViewGroup ?: return@hookAfterMethod

                val aioMsgItem = FieldUtils.create(uiState)
                    .typed(AIOMsgItem::class.java.superclass as Class<*>)
                    .getOrNull() as? AIOMsgItem ?: return@hookAfterMethod

                if (aioMsgItem is GrayTipsMsgItem) return@hookAfterMethod

                activeDecorators.forEach {
                    it.onGetViewNt(rootView, aioMsgItem.msgRecord, param)
                }
            }.onFailure { e ->
                Log.e("AIOViewUpdate Error", e)
            }
        }
    }
}

fun interface OnAIOViewUpdate {
    fun onGetViewNt(
        rootView: ViewGroup,
        chatMessage: MsgRecord,
        param: MethodHookParam
    )
}
