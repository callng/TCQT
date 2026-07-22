package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.view.View
import android.widget.LinearLayout
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.getStaticObject
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import java.lang.Thread.sleep
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

@RegisterAction
class MultiSelectRecall : IAction {

    override val name: String get() = "消息多选撤回"
    override val desc: String get() = "启用本功能后,消息多选模式下可批量撤回选中消息,非管理员也可使用。"
    override val uiTab: String get() = "界面"

    private var multiSelectBarVM: Any? = null

    override fun onRun(app: Application, process: ActionProcess) {
        Reflection.init()

        Reflection.createVM.hookAfter { param ->
            multiSelectBarVM = param.result
        }

        Reflection.operationInvoke.hookAfter { param ->
            val layout = param.result as? LinearLayout ?: return@hookAfter
            injectRecallButton(layout, param.thisObject)
        }
    }

    override val key: String get() = "multi_select_recall"

    private fun injectRecallButton(
        operationLayout: LinearLayout,
        operationLambda: Any
    ) {
        val vb = operationLambda.getObject($$"this$0")

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
        runCatching {
            val vm = multiSelectBarVM
            val forward = Reflection.multiForwardClass.getStaticObject("a")
            val context = Reflection.getContext.invoke(vm)
            val msgList = Reflection.getMsgList.invoke(forward, context) as List<AIOMsgItem>

            if (msgList.isEmpty()) {
                Toasts.error("AIOMsgItem is empty")
                return
            }

            ModuleScope.launchIO {
                val list = CopyOnWriteArrayList(msgList)
                list.forEachIndexed { index, item ->
                    recallSingleItem(item)
                    if (index >= 10) {
                        delay(300L.milliseconds)
                    }
                }
            }

            QQInterfaces.topActivity.onBackPressed()
        }.onFailure {
            Log.e("performBatchRecall", it)
        }
    }

    private fun recallSingleItem(msg: AIOMsgItem) {
        val record = msg.msgRecord
        val contact = Contact(record.chatType, record.peerUid, record.guildId)

        recallWithRetry(contact, record.msgId, retry = 3)
    }

    private fun recallWithRetry(
        contact: Contact,
        msgId: Long,
        retry: Int
    ) {
        QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgId)) { code, err ->
            if (code == 0) return@recallMsg

            if (retry > 0) {
                sleep(200L)
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
            }.toHostClass()

            val operationLambda = $$"$${barVB.name}$mOperationLayout$2".toHostClass()

            multiForwardClass =
                "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.b".toHostClass()

            getMsgList = multiForwardClass.findMethod {
                ambiguityStrategy = first
                returnType = list
                paramCount = 1
            }

            getContext = "com.tencent.mvi.mvvm.framework.FrameworkVM".toHostClass().findMethod {
                ambiguityStrategy = first
                returnType = getMsgList.parameterTypes[0].superclass
                paramCount = 0
            }

            makeView = barVB.findMethod {
                returnType = view
                paramCount = 4
                paramTypes = arrayOf(barVB, int, int, View.OnClickListener::class.java)
            }

            createVM = barVB.findMethod {
                returnType = "com.tencent.mvi.mvvm.BaseVM".toHostClass()
                paramCount = 0
            }

            operationInvoke = operationLambda.findMethod {
                name = "invoke"
            }
        }
    }
}
