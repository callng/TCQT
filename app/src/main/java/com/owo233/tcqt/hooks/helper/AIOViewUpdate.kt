package com.owo233.tcqt.hooks.helper

import android.app.Application
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.func.activity.AitChameleon
import com.owo233.tcqt.hooks.func.activity.RecallHeaderTip
import com.owo233.tcqt.hooks.func.activity.ShowMsgInfo
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObjectByType
import com.owo233.tcqt.utils.reflect.getObjectByTypeOrNull
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msg.GrayTipsMsgItem
import com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB
import com.tencent.mobileqq.aio.msglist.holder.AIOMsgItemUIState
import com.tencent.mvi.base.mvi.MviUIState
import com.tencent.qqnt.aio.holder.IMsgItemMviUIState
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class AIOViewUpdate : AlwaysRunAction() {

    override val key: String = "AIOViewUpdate"

    private val decorators: Array<OnAIOViewUpdate> = arrayOf(
        ShowMsgInfo(),
        RecallHeaderTip(),
        AitChameleon()
    )

    override fun onRun(app: Application, process: ActionProcess) {
        val activeDecorators = decorators
            .filterIsInstance<IAction>()
            .filter { it.canRun() && it.onInit() }
            .filterIsInstance<OnAIOViewUpdate>()
            .takeIf { it.isNotEmpty() } ?: return

        AIOBubbleMsgItemVB::class.java.findMethod {
            returnType = void
            visibility = public
            paramTypes(MviUIState::class.java)
        }.hookAfter { param ->
            val mviUIState = param.args[0] as IMsgItemMviUIState

            if (mviUIState is AIOMsgItemUIState.AIOMsgItemState) {
                val view = param.thisObject.getObjectByType<View>() as ViewGroup
                val aIOMsgItem = mviUIState.getObjectByTypeOrNull(
                    AIOMsgItem::class.java.superclass as Class<*>) as AIOMsgItem

                if (aIOMsgItem !is GrayTipsMsgItem) {
                    activeDecorators.forEach {
                        it.onGetViewNt(view, aIOMsgItem.msgRecord, param)
                    }
                }
            }
        }
    }
}

fun interface OnAIOViewUpdate {

    fun onGetViewNt(
        view: ViewGroup,
        msgRecord: MsgRecord,
        param: MethodHookParam
    )
}
