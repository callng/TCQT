package com.owo233.tcqt.features.hooks.func.activity

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.foundation.extensions.launchWithCatch
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.foundation.internal.QQInterfaces
import com.owo233.tcqt.foundation.utils.context.ContextUtils
import com.owo233.tcqt.foundation.utils.getObjectField
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.new
import com.owo233.tcqt.foundation.utils.paramCount
import com.owo233.tcqt.foundation.utils.toClass
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

@RegisterAction
@RegisterSetting(
    key = "multi_select_recall",
    name = "消息多选撤回",
    type = SettingType.BOOLEAN,
    desc = "启用本功能后,消息多选模式下可批量撤回选中消息,非管理员也可使用。",
    uiTab = "界面"
)
class MultiSelectRecall : IAction {

    private var multiSelectBarVM: Any? = null

    override fun onRun(ctx: Context, process: ActionProcess) {
        Reflection.init()

        Reflection.createVM.hookAfterMethod { param ->
            multiSelectBarVM = param.result
        }

        Reflection.operationInvoke.hookAfterMethod { param ->
            val layout = param.result as? LinearLayout ?: return@hookAfterMethod
            injectRecallButton(layout, param.thisObject)
        }
    }

    override val key: String get() = GeneratedSettingList.MULTI_SELECT_RECALL

    private fun injectRecallButton(
        operationLayout: LinearLayout,
        operationLambda: Any
    ) {
        val vb = operationLambda.getObjectField($$"this$0") ?: return

        val recallBtn = Reflection.makeView.invoke(
            null,
            vb,
            R.drawable.ic_action_recall,
            R.drawable.ic_action_recall,
            View.OnClickListener { performBatchRecall() }
        ) as View

        val index = (operationLayout.childCount - 2).coerceAtLeast(0)
        operationLayout.addView(recallBtn, index)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun performBatchRecall() {
        val vm = multiSelectBarVM ?: return
        val forward = Reflection.multiForwardClass.new()
        val context = Reflection.getContext.invoke(vm)
        val msgList = Reflection.getMsgList.invoke(forward, context) as List<AIOMsgItem>

        if (msgList.isEmpty()) return

        GlobalScope.launchWithCatch {
            val list = CopyOnWriteArrayList(msgList)
            list.forEachIndexed { index, item ->
                recallSingleItem(item)
                if (index >= 10) {
                    delay(300)
                }
            }
        }

        ContextUtils.getCurrentActivity()?.onBackPressed()
    }

    private fun recallSingleItem(msg: AIOMsgItem) {
        val record = msg.msgRecord
        val contact = Contact(record.chatType, record.peerUid, record.guildId)

        recallWithRetry(contact, record.msgId, retry = 1)
    }

    private fun recallWithRetry(
        contact: Contact,
        msgId: Long,
        retry: Int
    ) {
        QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgId)) { code, err ->
            if (code == 0) return@recallMsg

            if (retry > 0) {
                recallWithRetry(contact, msgId, retry - 1)
            } else {
                Log.e("尝试撤回消息ID为 $msgId 时失败, errCode:$code, errStr:$err")
            }
        }
    }

    private object Reflection {

        lateinit var makeView: Method
        lateinit var createVM: Method
        lateinit var getMsgList: Method
        lateinit var getContext: Method
        lateinit var operationInvoke: Method
        lateinit var multiForwardClass: Class<*>

        fun init() {
            val barVB = if (HookEnv.isQQ()) {
                "com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVB"
            } else {
                "com.tencent.tim.aio.inputbar.TimMultiSelectBarVB"
            }.toClass

            val operationLambda = $$"$${barVB.name}$mOperationLayout$2".toClass

            multiForwardClass = "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.b".toClass

            getMsgList = multiForwardClass.declaredMethods.single { method ->
                method.returnType == List::class.java && method.paramCount == 1
            }

            getContext = "com.tencent.mvi.mvvm.framework.FrameworkVM".toClass
                .declaredMethods
                .first { method ->
                    method.returnType == getMsgList.parameterTypes[0].superclass && method.paramCount == 0
                }

            makeView = barVB.declaredMethods.single { method ->
                val parameter = method.parameterTypes
                method.returnType == View::class.java && method.paramCount == 4
                        && parameter[0] == barVB && parameter[1] == Int::class.javaPrimitiveType
                        && parameter[2] == Int::class.javaPrimitiveType
                        && parameter[3] == View.OnClickListener::class.java
            }

            createVM = barVB.declaredMethods.single { method ->
                method.returnType == "com.tencent.mvi.mvvm.BaseVM".toClass && method.paramCount == 0
            }

            operationInvoke = operationLambda.declaredMethods.first { method ->
                method.name == "invoke"
            }
        }
    }
}
