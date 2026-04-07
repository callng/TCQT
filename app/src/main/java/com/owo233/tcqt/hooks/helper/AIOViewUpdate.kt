package com.owo233.tcqt.hooks.helper

import android.app.Application
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.func.activity.AitChameleon
import com.owo233.tcqt.hooks.func.activity.ShowMsgInfo
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msg.GrayTipsMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class AIOViewUpdate : AlwaysRunAction() {

    private val vbClass by lazy { "com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB".toHostClass() }
    private val mviStateClass by lazy { "com.tencent.mvi.base.mvi.MviUIState".toHostClass() }

    private val decorators: Array<OnAIOViewUpdate> = arrayOf(
        ShowMsgInfo(),
        AitChameleon()
    )

    override fun onRun(app: Application, process: ActionProcess) {
        val activeDecorators = decorators
            .filterIsInstance<IAction>()
            .filter { it.canRun() }
            .filterIsInstance<OnAIOViewUpdate>()
            .takeIf { it.isNotEmpty() } ?: return

        val targetMethod = vbClass.findMethod {
            returnType = void
            visibility = public
            paramTypes(mviStateClass)
        }

        targetMethod.hookAfter { param ->
            runCatching {
                val host = param.thisObject
                val uiState = param.args[0] ?: return@hookAfter

                val rootView = FieldUtils.create(host)
                    .typed<View>()
                    .getOrNull() as? ViewGroup ?: return@hookAfter

                val aioMsgItem = FieldUtils.create(uiState)
                    .typed(AIOMsgItem::class.java.superclass as Class<*>)
                    .getOrNull() as? AIOMsgItem ?: return@hookAfter

                if (aioMsgItem is GrayTipsMsgItem) return@hookAfter

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
