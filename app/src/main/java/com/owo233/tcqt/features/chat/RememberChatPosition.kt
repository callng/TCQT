package com.owo233.tcqt.features.chat

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.SearchScope
import com.owo233.tcqt.core.reflect.Visibility
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.findMethodOrNull
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import mqq.app.Foreground
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
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
    private const val UNREAD = "chatPositionUnread"

    private class Session(
        val key: String,
        val contact: Contact,
        var restore: ChatPosition?,
        var restoreUnread: Boolean,
        readSeq: Long,
    ) {
        val reading = ChatReadingProgress(readSeq)
        var list: WeakReference<Any>? = null
        var returnNavigation: ChatPosition? = null
        var latestSeq = 0L
        var latestRequested = false
    }

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
        UNREAD to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.reserve1.unreadbubble")
            matcher {
                paramCount(0); returnType("void")
                usingEqStrings("notifyLocalUnreadChanged unread = ")
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
        val notifyUnread = requireMethod(UNREAD)
        val unreadClass = notifyUnread.declaringClass
        val unreadRuntime = unreadClass.getMethod("getMContext")
        val intentClass = "com.tencent.mvi.base.route.MsgIntent".toClass
        val routerClass = "com.tencent.mvi.base.route.d".toClass
        // 路由 getter 的返回接口也会混淆，沿 MsgIntent 分发接口找实际返回类型。
        val readRouter = readRuntime.returnType.methods.single {
            it.parameterCount == 0 && routerClass.isAssignableFrom(it.returnType)
        }
        val dispatch = routerClass.findMethod {
            paramTypes(intentClass); returnType = Void.TYPE; requireUnique = true
        }
        val navigateBySeq = $$"com.tencent.mobileqq.aio.event.MsgNavigationEvent$NavigateBySeqEvent".toClass
            .getConstructor(
                String::class.java, Long::class.java, Long::class.java, Boolean::class.java,
                "com.tencent.mobileqq.aio.reserve1.navigation.AIONavType".toClass,
                Boolean::class.java, Boolean::class.java, "com.tencent.mobileqq.aio.msg.MsgSimpleInfo".toClass,
                Int::class.java, "kotlin.jvm.internal.DefaultConstructorMarker".toClass,
            )
        val readFocus = unreadClass.findMethod {
            paramCount = 0; returnType = Long::class.java
            visibility = Visibility.PRIVATE; scope = SearchScope.DECLARED; requireUnique = true
        }
        // 原生 VM 的两个 long 分别是当前计数和上次发布的计数；点击行为也读取它们。
        val unreadCounts = unreadClass.declaredFields.filter {
            it.type == Long::class.java && !Modifier.isStatic(it.modifiers)
        }.onEach { it.isAccessible = true }
        check(unreadCounts.size == 2)
        val unreadState = $$"com.tencent.mobileqq.aio.reserve1.unreadbubble.UnreadBubbleUI$UnreadCntChanged".toClass
            .getConstructor(Long::class.java)
        val updateUnreadUI = unreadClass.getMethod("updateUI", "com.tencent.mvi.base.mvi.MviUIState".toClass)
        val scrollEventClass = $$"com.tencent.mobileqq.aio.reserve1.unreadbubble.UnreadMsgIntent$MsgListScrollEvent".toClass
        val receivedClass = $$"com.tencent.mobileqq.aio.reserve1.unreadbubble.UnreadMsgIntent$MsgReceive".toClass
        val receivedMessages = receivedClass.findMethod {
            paramCount = 0; returnType = List::class.java; requireUnique = true
        }
        val visibleMessages = WeakHashMap<Any, Pair<ChatPosition, Long>>()
        val main = Handler(Looper.getMainLooper())

        fun readPosition(list: Any): ChatPosition? {
            val recycler = readRecycler.invoke(list) as ViewGroup
            if (recycler.isEmpty() || recycler.height == 0) return null
            val manager = readManager.invoke(recycler)
            val first = firstVisible.invoke(manager) as Int
            val last = lastVisible.invoke(manager) as Int
            if (first < 0) return null
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
                return ChatPosition(record.msgId, record.msgSeq, offset)
            }
            return null
        }

        readRecycler.hookAfter { hook ->
            val list = requireNotNull(hook.thisObject)
            // QQ 构建 View 树时还未绑定 runtime，等绑定后的列表访问再关联会话。
            val runtime = readRuntime.invoke(list) ?: return@hookAfter
            val aioParam = readParam.invoke(runtime)
            sessions[aioParam]?.let { if (it.list == null) it.list = WeakReference(list) }
        }
        val unreadVB = "com.tencent.mobileqq.aio.reserve1.unreadbubble.UnreadBubbleVB".toClass
        val readUnreadView = unreadVB.getMethod("getHostView")
        unreadVB.findMethod {
            name = "onCreateView"; paramCount = 1; returnType = View::class.java
            scope = SearchScope.DECLARED; requireUnique = true
        }.hookAfter { hook ->
            val vb = requireNotNull(hook.thisObject)
            hook.result = ChatUnreadButtonView(hook.result as TextView) {
                val runtime = readRuntime.invoke(vb)
                val session = sessions[readParam.invoke(runtime)]
                val target = session?.reading?.returnTarget(session.latestSeq)
                if (target == null) false else {
                    session.returnNavigation = target
                    // 原生导航负责取回已不在列表中的消息；定位策略负责恢复阅读时的顶部偏移。
                    dispatch.invoke(readRouter.invoke(runtime), navigateBySeq.newInstance(
                        "tcqt_reading_return", target.msgSeq, target.msgId.coerceAtLeast(0L), true,
                        null, false, true, null, 0, null,
                    ))
                    true
                }
            }
        }
        unreadVB.getMethod("handleUIState", "com.tencent.mvi.base.mvi.MviUIState".toClass).hookAfter { hook ->
            (readUnreadView.invoke(hook.thisObject) as? ChatUnreadButtonView)?.syncFromNative()
        }
        unreadVB.findMethod {
            name = "onReuseView"; paramCount = 1; returnType = Void.TYPE
            scope = SearchScope.DECLARED; requireUnique = true
        }.hookAfter { hook ->
            (readUnreadView.invoke(hook.thisObject) as? ChatUnreadButtonView)?.syncFromNative()
        }

        fun refreshUnread(vm: Any, session: Session) {
            val count = ChatPosition.unreadCount(session.reading.readSeq, session.latestSeq)
            if (unreadCounts.all { it.getLong(vm) == count }) return
            // 沿用原生的底部跟随判断，等实际可见消息推进阅读进度，避免气泡先出现再归零。
            if (count > 0L && unreadCounts.all { it.getLong(vm) == 0L } && readFocus.invoke(vm) == -1L) return
            unreadCounts.forEach { it.setLong(vm, count) }
            updateUnreadUI.invoke(vm, unreadState.newInstance(count))
            // 保留原生气泡归零时的已读确认，恢复位置本身不提前确认。
            if (count == 0L && Foreground.isCurrentProcessForeground()) {
                QQInterfaces.msgService.setMsgRead(session.contact) { _, _ -> }
            }
        }

        // 构造参数的第三项是底部可见消息，不依赖混淆后的 getter 名称。
        scrollEventClass.getDeclaredConstructor(String::class.java, itemClass, itemClass, itemClass)
            .hookAfter { hook ->
                val bottom = hook.args[2] ?: return@hookAfter
                val record = readRecord.invoke(bottom) as MsgRecord
                if (record.msgSeq <= 0L) return@hookAfter
                val latest = hook.args[3]?.let { (readRecord.invoke(it) as MsgRecord).msgSeq } ?: 0L
                visibleMessages[requireNotNull(hook.thisObject)] = ChatPosition(record.msgId, record.msgSeq, 0) to latest
            }

        val unreadEvent = unreadClass.findMethod {
            paramTypes(intentClass); returnType = Void.TYPE
            visibility = Visibility.PRIVATE; scope = SearchScope.DECLARED; requireUnique = true
        }
        unreadEvent.hookBefore { hook ->
            val vm = requireNotNull(hook.thisObject)
            val aioParam = readParam.invoke(unreadRuntime.invoke(vm))
            val session = sessions[aioParam] ?: return@hookBefore
            if (session.restore != null) return@hookBefore
            val event = hook.args[0]
            visibleMessages.remove(event)?.let { (bottom, latest) ->
                if (session.returnNavigation == null) {
                    val first = if (bottom.msgSeq >= session.reading.furthestVisibleSeq) {
                        session.list?.get()?.let(::readPosition)
                    } else null
                    session.reading.onVisible(bottom, first)
                }
                session.latestSeq = maxOf(session.latestSeq, latest)
            }
            if (receivedClass.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                val messages = receivedMessages.invoke(event) as List<MsgRecord>
                val latest = messages.asSequence().filter {
                    it.msgType != 1 && it.peerUid == session.contact.peerUid && it.chatType == session.contact.chatType
                }.maxOfOrNull { it.msgSeq } ?: 0L
                session.latestSeq = maxOf(session.latestSeq, latest)
            }
            if (!session.latestRequested && session.reading.readSeq > 0L) {
                session.latestRequested = true
                // 只读本地最新一条，不触发首屏加载或更改内核的未读标记。
                QQInterfaces.msgService.getLatestDbMsgs(session.contact, 1) { code, _, records ->
                    if (code != 0) return@getLatestDbMsgs
                    val latest = records.maxOfOrNull { it.msgSeq } ?: 0L
                    main.post {
                        if (sessions[aioParam] !== session) return@post
                        session.latestSeq = maxOf(session.latestSeq, latest)
                        if (session.restoreUnread) refreshUnread(vm, session)
                    }
                }
            }
        }
        unreadEvent.hookAfter { hook ->
            val aioParam = readParam.invoke(unreadRuntime.invoke(hook.thisObject))
            val session = sessions[aioParam] ?: return@hookAfter
            if (session.restoreUnread && session.restore == null) refreshUnread(requireNotNull(hook.thisObject), session)
        }
        notifyUnread.hookBefore { hook ->
            val aioParam = readParam.invoke(unreadRuntime.invoke(hook.thisObject))
            val session = sessions[aioParam] ?: return@hookBefore
            if (!session.restoreUnread || session.restore != null) return@hookBefore
            refreshUnread(requireNotNull(hook.thisObject), session)
            hook.result = null
        }

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
                val previous = (positions[key] as? String)?.let(ChatPosition::decode)
                val saved = if (explicitNavigation) null else previous
                sessions[aioParam] = Session(key, contact, saved, saved != null, previous?.readSeq ?: 0L)
                if (saved == null) return@hookBefore
                extras.putLong("key_navigate_msgid", saved.msgId.coerceAtLeast(0L))
                extras.putLong("key_navigate_msgseq", saved.msgSeq.coerceAtLeast(0L))
            }

        focus.hookBefore { hook ->
            val list = focusList.get(hook.thisObject)
            val aioParam = readParam.invoke(readRuntime.invoke(list))
            val session = sessions[aioParam] ?: return@hookBefore
            val entering = session.restore != null
            val saved = session.returnNavigation ?: session.restore ?: return@hookBefore
            val state = hook.args[0] as List<*>
            val navigation = stateExtras.invoke(state) as? Bundle ?: return@hookBefore
            if (!navigation.containsKey("navigate_id") && !navigation.containsKey("navigate_seq") &&
                !navigation.containsKey("navigate_time")) return@hookBefore
            session.restore = null
            session.returnNavigation = null
            if (!saved.matchesNavigation(
                    navigation.getLong("navigate_id"), navigation.getLong("navigate_seq"),
                    navigation.getLong("navigate_time"),
                )) {
                if (entering) session.restoreUnread = false
                return@hookBefore
            }
            val messages = state.map { item ->
                if (itemClass.isInstance(item)) {
                    val record = readRecord.invoke(item) as MsgRecord
                    record.msgId to record.msgSeq
                } else 0L to 0L
            }
            val index = saved.indexIn(messages)
            if (index < 0) {
                if (entering) {
                    session.restoreUnread = false
                    positions.remove(session.key)
                }
                return@hookBefore
            }
            val recycler = readRecycler.invoke(list)
            val manager = readManager.invoke(recycler)
            // 使用宿主的锚点滚动，保留其异步布局处理，同时跳过原生定位的高亮和居中。
            // 返回 false 仅表示已有相同的待布局锚点，同样不能再让原生策略覆盖它。
            scroll.invoke(manager, index, saved.offset, false, null)
            if (!entering) session.reading.onReturned()
            hook.result = null
        }

        // onDestroy 时子 ViewBinder 已被销毁；在 destroy 入口读取仍绑定着消息的 holder。
        baseVB.getDeclaredMethod("destroy").hookBefore { hook ->
            val list = hook.thisObject
            if (!listClass.isInstance(list)) return@hookBefore
            val aioParam = readParam.invoke(readRuntime.invoke(list))
            val session = sessions.remove(aioParam) ?: return@hookBefore
            if (session.restore != null) return@hookBefore
            val position = readPosition(requireNotNull(list)) ?: return@hookBefore
            positions.put(session.key, position.copy(readSeq = session.reading.readSeq).encode())
        }
    }
}
