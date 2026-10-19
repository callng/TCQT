package com.owo233.tcqt.features.message

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.findMethods
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.getStaticObject
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.delay
import java.lang.Thread.sleep
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.view.isEmpty

@RegisterAction
object MultiSelectRecall : Feature(
    key = "multi_select_recall",
    name = "消息多选撤回",
    desc = "启用本功能后,消息多选模式下可批量撤回选中消息,非管理员也可使用。",
) {
    override fun install() {
        Reflection.init()

        val barNames = if (HookEnv.isQQ()) {
            listOf(
                "com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVB",
                "com.tencent.mobileqq.aio.input.multiselect.AskQBaoMultiSelectBarVB"
            )
        } else {
            listOf("com.tencent.tim.aio.inputbar.TimMultiSelectBarVB")
        }
        barNames.forEachIndexed { index, name ->
            // AskQBao is absent on older QQ versions; still install the normal toolbar.
            val barVB = if (index == 0) name.toHostClass()
            else runCatching { name.toHostClass() }.getOrNull() ?: return@forEachIndexed
            installToolbar(barVB)
        }
    }

    private fun installToolbar(barVB: Class<*>) {
        val makeView = barVB.findMethod {
            returnType = view
            isStatic = true
            paramTypes = arrayOf(barVB, int, int, View.OnClickListener::class.java)
        }
        val operationInvoke = $$"$${barVB.name}$mOperationLayout$2".toHostClass().findMethod {
            name = "invoke"
            paramCount = 0
            includeBridge = false
        }

        // The AskQBao container rebuilds its children on mode/comic changes, and updates
        // their alpha on selection changes. Match its own public state setters by shape.
        val operationClass = operationInvoke.returnType
        if (operationClass != LinearLayout::class.java &&
            LinearLayout::class.java.isAssignableFrom(operationClass)
        ) {
            operationClass.findMethods {
                scope = declared
                visibility = public
                isStatic = false
                returnType = void
            }.filter { method ->
                val types = method.parameterTypes.toList()
                types == listOf(Integer.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE) ||
                    types == listOf(java.lang.Boolean.TYPE) || types == listOf(Integer.TYPE)
            }.forEach { method ->
                method.hookAfter { param ->
                    val layout = param.thisObject as LinearLayout
                    (layout.getTag(R.drawable.ic_action_recall) as? Runnable)?.run()
                }
            }
        }

        operationInvoke.hookAfter { param ->
            val layout = param.result as? LinearLayout ?: return@hookAfter
            val vb = param.thisObject.getObject($$"this$0")
            val updateButton = Runnable {
                MultiSelectRecallUi.ensureButton(layout) {
                    makeView.invoke(
                        null, vb, R.drawable.ic_action_recall, R.drawable.ic_action_recall,
                        View.OnClickListener { performBatchRecall(vb) }
                    ) as View
                }
            }
            layout.setTag(R.drawable.ic_action_recall, updateButton)
            updateButton.run()
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun performBatchRecall(vb: Any) {
        runCatching {
            val vm = Reflection.getVM.invoke(vb)
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

            QQInterfaces.topActivity?.onBackPressed()
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

        lateinit var getVM: Method
        lateinit var getMsgList: Method
        lateinit var getContext: Method
        lateinit var multiForwardClass: Class<*>

        fun init() {
            multiForwardClass =
                "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.b".toHostClass()

            getMsgList = multiForwardClass.findMethod {
                returnType = list
                paramCount = 1
            }

            getContext = "com.tencent.mvi.mvvm.framework.FrameworkVM".toHostClass().findMethod {
                returnType = getMsgList.parameterTypes[0].superclass
                paramCount = 0
            }

            getVM = "com.tencent.mvi.mvvm.framework.FrameworkVB".toHostClass().findMethod {
                name = "getMUIModel"
                returnType = "com.tencent.mvi.mvvm.BaseVM".toHostClass()
                paramCount = 0
            }
        }
    }
}

/** Layout handling is kept independent of QQ classes so it can be exercised on Android. */
internal object MultiSelectRecallUi {
    @SuppressLint("ResourceType")
    fun ensureButton(layout: LinearLayout, createButton: () -> View) {
        val id = R.drawable.ic_action_recall
        val row = if (layout.orientation == LinearLayout.VERTICAL) {
            layout.getChildAt(layout.childCount - 1) as? LinearLayout ?: return
        } else layout
        val hostAlpha = (0 until row.childCount).asSequence()
            .map { row.getChildAt(it) }
            .firstOrNull { it.id != id && it.isClickable }?.alpha ?: 1f
        layout.findViewById<View>(id)?.let {
            it.alpha = hostAlpha
            return
        }

        if (layout.orientation == LinearLayout.VERTICAL) {
            // QQ builds five fixed cells per row. Appending a sixth item is clipped;
            // putting a button directly in the vertical root creates an unwanted row.
            val slot = (0 until row.childCount).asSequence()
                .mapNotNull { row.getChildAt(it) as? FrameLayout }
                .firstOrNull { it.isEmpty() } ?: return
            // The last occupied cell is More. Swap whole cells so its click listener,
            // accessibility label and the weighted gaps stay intact, with More last.
            val slotIndex = row.indexOfChild(slot)
            val moreSlot = (0 until slotIndex).asSequence()
                .mapNotNull { row.getChildAt(it) as? FrameLayout }
                .lastOrNull { !it.isEmpty() } ?: return
            val moreIndex = row.indexOfChild(moreSlot)
            val button = createButton().apply {
                this.id = id
                contentDescription = "多选撤回"
                alpha = hostAlpha
                setPadding(0, 0, 0, 0)
            }
            slot.addView(button, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            row.removeView(slot)
            row.removeView(moreSlot)
            row.addView(slot, moreIndex)
            row.addView(moreSlot, slotIndex)
        } else {
            val button = createButton().apply {
                this.id = id
                contentDescription = "多选撤回"
                alpha = hostAlpha
            }
            val index = (row.childCount - 2).coerceAtLeast(0)
            row.addView(button, index)
            row.addView(View(row.context), index + 1, LinearLayout.LayoutParams(0, 0, 1f))
        }
    }
}
