package com.owo233.tcqt.features.internal.pipeline

import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.api.PipelineDecorator
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.getObjectByTypeOrNull
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msg.GrayTipsMsgItem
import com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB
import com.tencent.mobileqq.aio.msglist.holder.AIOMsgItemUIState
import com.tencent.qqnt.aio.holder.IMsgItemMviUIState
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
object AIOViewUpdate : InfraTask(
    key = "AIOViewUpdate",
) {

    override fun install() {
        val activeDecorators = PipelineDecorators.all(OnAIOViewUpdate::class.java)
            .filter { it.isAvailable() }
            .onEach { it.activate() }
            .takeIf { it.isNotEmpty() } ?: return

        AIOBubbleMsgItemVB::class.java.findMethod {
            returnType = void
            visibility = public
            paramTypes(IMsgItemMviUIState::class.java)
        }.hookAfter { param ->
            val mviUIState = param.args[0] as? IMsgItemMviUIState ?: return@hookAfter

            if (mviUIState is AIOMsgItemUIState.AIOMsgItemState) {
                val view = (requireNotNull(param.thisObject).getObjectByTypeOrNull(View::class.java) as? ViewGroup) ?: return@hookAfter
                val aIOMsgItem = mviUIState.getObjectByTypeOrNull(
                    AIOMsgItem::class.java.superclass as Class<*>
                ) as? AIOMsgItem ?: return@hookAfter

                if (aIOMsgItem !is GrayTipsMsgItem) {
                    activeDecorators.forEach {
                        it.onGetViewNt(view, aIOMsgItem.msgRecord, param)
                    }
                }
            }
        }
    }
}

fun interface OnAIOViewUpdate : PipelineDecorator {

    fun onGetViewNt(
        view: ViewGroup,
        msgRecord: MsgRecord,
        param: MethodHookParam
    )
}
