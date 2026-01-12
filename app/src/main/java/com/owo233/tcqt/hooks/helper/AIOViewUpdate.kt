package com.owo233.tcqt.hooks.helper

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.children
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.func.activity.ShowMsgInfo
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.toClass
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msg.GrayTipsMsgItem
import com.tencent.qqnt.aio.holder.template.BubbleLayoutCompatPress
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

                val rootLayout = FieldUtils.create(host)
                    .typed<View>()
                    .getOrThrow() as? ViewGroup ?: return@hookAfterMethod

                val aioMsgItem = FieldUtils.create(uiState)
                    .typed(AIOMsgItem::class.java.superclass as Class<*>)
                    .getOrThrow() as? AIOMsgItem ?: return@hookAfterMethod

                if (aioMsgItem is GrayTipsMsgItem) return@hookAfterMethod

                rootLayout.children
                    .filterIsInstance<BubbleLayoutCompatPress>()
                    .firstOrNull()
                    ?.let { bubbleLayout ->
                        val container = bubbleLayout.getOrCreateHookContainer()
                        val msgRecord = aioMsgItem.msgRecord

                        activeDecorators.forEach { it.onUpdate(container, msgRecord) }
                    }
            }.onFailure { e ->
                Log.e("AIOViewUpdate Error", e)
            }
        }
    }

    private fun BubbleLayoutCompatPress.getOrCreateHookContainer(): FrameLayout {
        val firstChild = getChildAt(0)

        if (firstChild?.tag == "TCQT_HOOK") {
            return firstChild as FrameLayout
        }

        val context = this.context
        removeAllViews()

        return FrameLayout(context).apply {
            tag = "TCQT_HOOK"
            addView(LinearLayout(context).apply {
                addView(firstChild)
            })
        }.also {
            addView(it)
        }
    }
}

fun interface OnAIOViewUpdate {
    fun onUpdate(frameLayout: FrameLayout, msgRecord: MsgRecord)
}
