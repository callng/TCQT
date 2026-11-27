package com.owo233.tcqt.features.chat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.SearchScope
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.findMethodOrNull
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File
import java.util.WeakHashMap
import androidx.core.view.isEmpty

@RegisterAction
object RememberChatPosition : Feature(
    key = "remember_chat_position",
    name = "记住消息位置",
    desc = "退出好友或群聊页面后，再次进入时回到上次浏览的消息位置。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
), DexKitTask {

    private const val PARAM = "com.tencent.aio.data.AIOParam"
    private const val SESSION = "com.tencent.aio.data.AIOSession"
    private const val LIST = "com.tencent.mobileqq.aio.msglist.AIOMsgListVB"
    private const val STATE = "com.tencent.aio.part.root.panel.content.firstLevel.msglist.mvx.state.MsgListState"
    private const val LIST_API = "com.tencent.aio.api.list.IListUIOperationApi"
    private const val BUBBLE = "com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB"
    private const val ITEM = "com.tencent.mobileqq.aio.msg.AIOMsgItem"
    private const val RECYCLER = "androidx.recyclerview.widget.AIORecycleView"
    private const val PRELOAD = "chatPositionPreload"
    private const val FOCUS = "chatPositionFocus"
    private const val CONTACT = "chatPositionContact"

    private class Session(val key: String, var restore: ChatPosition?)

    private val positions by lazy {
        SettingsStore(File(HookEnv.moduleDataPath, "chat_position"), "positions")
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        PRELOAD to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.factory.param")
            matcher {
                returnType("void")
                usingEqStrings("preLoadMsg", "key_navigate_msgid", "key_navigate_msgseq")
            }
        },
        FOCUS to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist")
            matcher {
                paramTypes(STATE, LIST_API)
                returnType("void")
                usingEqStrings("[createFocusIndex]: navigationSeq is ")
            }
        },
        CONTACT to FindMethod().apply {
            matcher {
                declaredClass("com.tencent.mobileqq.aio.utils.AIOUtil")
                paramTypes(SESSION)
                returnType("com.tencent.qqnt.kernelpublic.nativeinterface.Contact")
                usingEqStrings("aioSession")
            }
        },
    )

    override fun install() {
        val sessions = WeakHashMap<Any, Session>()
        val paramClass = PARAM.toClass
        val listClass = LIST.toClass
        val itemClass = ITEM.toClass
        val bubbleClass = BUBBLE.toClass
        val recyclerClass = RECYCLER.toClass
        val managerClass = "androidx.recyclerview.widget.AIOLayoutManager".toClass
        val readExtras = paramClass.findMethod {
            paramCount = 0; returnType = Bundle::class.java; requireUnique = true
        }
        val readSession = paramClass.findMethod {
            paramCount = 0; returnType = SESSION.toClass; requireUnique = true
        }
        val readRuntime = listClass.findMethod {
            name = $$"getMAIOContext$sdk_debug"; paramCount = 0
        }
        // 9.3.5～9.3.15 连 base VB、runtime 接口的包名也被混淆，沿实际类型读取。
        val baseVB = readRuntime.declaringClass
        val readParam = readRuntime.returnType.findMethod {
            paramCount = 0; returnType = paramClass; requireUnique = true
        }
        val makeContact = requireMethod(CONTACT)
        val contactOwner = makeContact.declaringClass.findField {
            type = makeContact.declaringClass; isStatic = true; requireUnique = true
        }.get(null)
        val readRecycler = listClass.findMethod {
            paramCount = 0; returnType = recyclerClass
            scope = SearchScope.DECLARED; requireUnique = true
        }
        val readManager = recyclerClass.findMethod {
            name = "getLayoutManager"; paramCount = 0; returnType = managerClass
        }
        val firstVisible = managerClass.getMethod("findFirstVisibleItemPosition")
        val lastVisible = managerClass.getMethod("findLastVisibleItemPosition")
        val findView = managerClass.getMethod("findViewByPosition", Int::class.java)
        val decoratedTop = managerClass.getMethod("getDecoratedTop", View::class.java)
        val findHolder = recyclerClass.getMethod("findViewHolderForAdapterPosition", Int::class.java)
        val readItem = bubbleClass.findMethod {
            paramCount = 0; returnType = itemClass; requireUnique = true
        }
        val readRecord = itemClass.getMethod("getMsgRecord")
        val stateExtras = STATE.toClass.getMethod("getExtra")
        val focus = requireMethod(FOCUS)
        val focusList = focus.declaringClass.findField {
            type = listClass; isStatic = false; requireUnique = true
        }
        val scroll = managerClass.declaredMethods.single {
            it.name.startsWith("scrollToPositionWithOffset") && it.parameterTypes.size == 4 &&
                it.parameterTypes.take(3) == listOf(Int::class.java, Int::class.java, Boolean::class.java)
        }.apply { isAccessible = true }

        // 首屏预加载也读取导航参数，必须在它开始前注入，不能等列表创建后再跳转。
        requireMethod(PRELOAD).declaringClass.getDeclaredConstructor(Context::class.java, paramClass)
            .hookBefore { hook ->
                val aioParam = requireNotNull(hook.args[1])
                val extras = readExtras.invoke(aioParam) as Bundle
                val contact = makeContact.invoke(contactOwner, readSession.invoke(aioParam)) as Contact
                val key = ChatPosition.conversationKey(
                    QQInterfaces.currentUin, contact.chatType, contact.peerUid,
                ) ?: return@hookBefore
                val explicitNavigation = listOf("key_navigate_msgid", "key_navigate_msgseq", "key_navigate_time")
                    .any { extras.getLong(it) > 0L }
                val saved = if (explicitNavigation) null else (positions[key] as? String)?.let(ChatPosition::decode)
                sessions[aioParam] = Session(key, saved)
                if (saved == null) return@hookBefore
                extras.putLong("key_navigate_msgid", saved.msgId.coerceAtLeast(0L))
                extras.putLong("key_navigate_msgseq", saved.msgSeq.coerceAtLeast(0L))
            }

        focus.hookBefore { hook ->
            val list = focusList.get(hook.thisObject)
            val aioParam = readParam.invoke(readRuntime.invoke(list))
            val session = sessions[aioParam] ?: return@hookBefore
            val saved = session.restore ?: return@hookBefore
            val state = hook.args[0] as List<*>
            val navigation = stateExtras.invoke(state) as? Bundle ?: return@hookBefore
            if (!navigation.containsKey("navigate_id") && !navigation.containsKey("navigate_seq") &&
                !navigation.containsKey("navigate_time")) return@hookBefore
            session.restore = null
            if (!saved.matchesNavigation(
                    navigation.getLong("navigate_id"), navigation.getLong("navigate_seq"),
                    navigation.getLong("navigate_time"),
                )) return@hookBefore
            val messages = state.map { item ->
                if (itemClass.isInstance(item)) {
                    val record = readRecord.invoke(item) as MsgRecord
                    record.msgId to record.msgSeq
                } else 0L to 0L
            }
            val index = saved.indexIn(messages)
            if (index < 0) {
                positions.remove(session.key)
                return@hookBefore
            }
            val recycler = readRecycler.invoke(list)
            val manager = readManager.invoke(recycler)
            // 使用宿主的锚点滚动，保留其异步布局处理，同时跳过原生定位的高亮和居中。
            // 返回 false 仅表示已有相同的待布局锚点，同样不能再让原生策略覆盖它。
            scroll.invoke(manager, index, saved.offset, false, null)
            hook.result = null
        }

        // onDestroy 时子 ViewBinder 已被销毁；在 destroy 入口读取仍绑定着消息的 holder。
        baseVB.getDeclaredMethod("destroy").hookBefore { hook ->
            val list = hook.thisObject
            if (!listClass.isInstance(list)) return@hookBefore
            val aioParam = readParam.invoke(readRuntime.invoke(list))
            val session = sessions.remove(aioParam) ?: return@hookBefore
            if (session.restore != null) return@hookBefore
            val recycler = readRecycler.invoke(list) as ViewGroup
            if (recycler.isEmpty() || recycler.height == 0) return@hookBefore
            val manager = readManager.invoke(recycler)
            val first = firstVisible.invoke(manager) as Int
            val last = lastVisible.invoke(manager) as Int
            if (first < 0) return@hookBefore
            for (index in first..last) {
                val holder = findHolder.invoke(recycler, index) ?: continue
                val readVB = holder.javaClass.findMethodOrNull {
                    paramCount = 0; returnType = baseVB; requireUnique = true
                } ?: continue
                val vb = readVB.invoke(holder)
                if (!bubbleClass.isInstance(vb)) continue
                val item = readItem.invoke(vb) ?: continue
                val record = readRecord.invoke(item) as MsgRecord
                if (record.msgId <= 0L && record.msgSeq <= 0L) continue
                val view = findView.invoke(manager, index) as? View ?: continue
                val margin = (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin
                val offset = (decoratedTop.invoke(manager, view) as Int) - margin - recycler.paddingTop
                positions.put(session.key, ChatPosition(record.msgId, record.msgSeq, offset).encode())
                break
            }
        }
    }
}
