package com.owo233.tcqt.features.appearance

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.reflect.callOriginal
import com.owo233.tcqt.features.appearance.chatgroups.ChatCategory
import com.owo233.tcqt.features.appearance.chatgroups.ChatGroupHeader
import com.owo233.tcqt.features.appearance.chatgroups.ChatGroupSession
import com.owo233.tcqt.features.appearance.chatgroups.RecentChatAccess
import com.owo233.tcqt.features.appearance.chatgroups.createChatGroupBar
import com.owo233.tcqt.features.appearance.chatgroups.projectChatItems
import com.owo233.tcqt.features.appearance.stickysearch.PinnedSearchBar
import com.owo233.tcqt.host.QQInterfaces
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.ref.WeakReference
import java.util.WeakHashMap

@RegisterAction(priority = ActionPriority.CRITICAL)
object ChatGroups : Feature(
    key = "chat_groups",
    name = "聊天分组",
    desc = "在消息页搜索框下按类型筛选会话，也可创建自定义分组。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
), DexKitTask {

    private val visibleCategories by multiIntOption(
        "categories", "显示分类", defaultValue = 63,
        options = listOf("群聊", "好友", "未读", "频道", "其他", "临时"),
    )
    private var config by stringOption("config", "自定义分组", isHide = true)

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        // Shared with StickySearchBar so the same host method is scanned only once.
        "stickySearchRegisterHeader" to FindMethod().apply {
            searchPackages("com.tencent.qqnt.chats.core.adapter")
            matcher {
                paramTypes("int", "android.view.View", "boolean")
                returnType = "void"
                usingEqStrings("view")
            }
        },
        "chatGroupsMergeList" to FindMethod().apply {
            matcher {
                declaredClass = "com.tencent.qqnt.chats.core.adapter.ChatsListAdapter"
                paramTypes("java.util.List")
                returnType = "java.util.List"
            }
        },
        "chatGroupsUpdateHeader" to FindMethod().apply {
            matcher {
                declaredClass = "com.tencent.qqnt.chats.core.ui.ChatsListVB"
                paramTypes("int", "android.view.View")
                returnType = "void"
            }
        },
        "chatGroupsRecentItem" to FindClass().apply {
            searchPackages("com.tencent.qqnt.chats.core.adapter.itemdata")
            matcher { usingEqStrings("RecentContactChatItem{ uid=") }
        },
        "chatGroupsFoldItem" to FindClass().apply {
            searchPackages("com.tencent.qqnt.chats.core.adapter.itemdata")
            matcher {
                superClass("java.lang.Object")
                usingEqStrings("foldedItems")
            }
        },
    )

    override fun install() {
        val register = requireMethod("stickySearchRegisterHeader")
        val merge = requireMethod("chatGroupsMergeList")
        val updateHeader = requireMethod("chatGroupsUpdateHeader")
        val adapterClass = "com.tencent.qqnt.chats.core.adapter.ChatsListAdapter".toClass
        val submit = adapterClass.getDeclaredMethod("submitList", List::class.java)
        val access = RecentChatAccess(requireClass("chatGroupsRecentItem"), requireClass("chatGroupsFoldItem"))
        val categories = listOf(ChatCategory.ALL) + ChatCategory.entries.drop(1)
            .filterIndexed { index, _ -> visibleCategories and (1 shl index) != 0 }
        val sessions = WeakHashMap<Any, ChatGroupSession>()

        "com.tencent.qqnt.chats.view.ClipSkinnableRecycleView".toClass
            .getDeclaredMethod("dispatchDraw", Canvas::class.java).hookReplace { chain ->
                val header = (chain.thisObject as View).getTag(R.id.tcqt_tag_chat_group_header)
                    as? ChatGroupHeader ?: return@hookReplace chain.proceed()
                val canvas = chain.args[0] as Canvas
                val saved = canvas.save()
                try {
                    // Hide scrolling rows behind the transparent strip; keep QQ's background.
                    canvas.clipOutRect(header.coveredRows)
                    chain.proceed()
                } finally {
                    canvas.restoreToCount(saved)
                }
            }

        register.hookAfter { param ->
            if (param.args[0] != -1) return@hookAfter
            // An outer After hook can retain the original argument even when StickySearchBar
            // replaced it in its inner Before hook. Resolve the native search from its binding.
            val header = PinnedSearchBar.messageHeader(param.args[1] as View) ?: return@hookAfter
            val adapter = param.thisObject
            val session = sessions.getOrPut(adapter) {
                val reference = WeakReference(adapter)
                lateinit var created: ChatGroupSession
                created = ChatGroupSession(categories, { config }, { config = it }, { QQInterfaces.currentUin }) {
                    reference.get()?.let { submit.invoke(it, created.source) }
                }
                created
            }
            session.syncAccount()
            val existing = session.header.get()
            val container = if (session.originalHeader.get() === header && existing != null) existing else {
                ChatGroupHeader(PinnedSearchBar.messagePlaceholder(header),
                    createChatGroupBar(header.context, session, access)).also {
                    session.originalHeader = WeakReference(header)
                    session.header = WeakReference(it)
                }
            }
            // Keep QQ's native search View and listeners; replace only its registered header container.
            register.callOriginal(adapter, -1, container, false)
        }

        updateHeader.hookAfter { param ->
            if (param.args[0] != -1) return@hookAfter
            val header = param.args[1] as View
            val container = sessions.values.firstOrNull { it.originalHeader.get() === header }
                ?.header?.get() as? ChatGroupHeader ?: return@hookAfter
            // QQ removes the native search from its parent after registering the header.
            // Reattach only after that update finishes, preserving the native View and listeners.
            if (container.search === header && header.parent !== container) {
                (header.parent as? ViewGroup)?.removeView(header)
                container.addView(header, 0, LinearLayout.LayoutParams(-1, header.layoutParams?.height ?: -2))
            }
        }

        merge.hookAfter { param ->
            val session = sessions[param.thisObject] ?: return@hookAfter
            session.syncAccount()
            @Suppress("UNCHECKED_CAST")
            val source = param.args[0] as List<Any>
            @Suppress("UNCHECKED_CAST")
            val rendered = param.result as List<Any>
            session.source = source
            val projected = projectChatItems(rendered, session.selected, session.groups,
                access::describe, access::foldedItems)
            session.empty = session.selected != ChatCategory.ALL.id && projected.none(access::isConversation)
            param.result = projected
        }
    }
}
