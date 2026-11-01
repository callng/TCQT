package com.owo233.tcqt.features.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.features.appearance.stickysearch.PinnedSearchBar
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

// The first search header can be registered before an EARLY background hook is ready.
@RegisterAction(priority = ActionPriority.CRITICAL)
object StickySearchBar : Feature(
    key = "sticky_search_bar",
    name = "搜索框吸顶",
    desc = "将消息和联系人页的原生搜索框固定在顶栏下方，滚动列表时保持可见。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
), DexKitTask {

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "stickySearchRegisterHeader" to FindMethod().apply {
            matcher {
                declaredClass = "com.tencent.qqnt.chats.core.adapter.ChatsListAdapter"
                paramTypes("int", "android.view.View", "boolean")
                returnType = "void"
                usingEqStrings("view")
            }
        },
    )

    override fun install() {
        val registerHeader = requireMethod("stickySearchRegisterHeader")
        // onCreateView is a Fragment lifecycle override, not an obfuscated name.
        val createContacts = "com.tencent.mobileqq.friend.contact.ContactsFragment".toClass
            .getDeclaredMethod("onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java, Bundle::class.java
            )

        // -1 is the search header, not a conversation. Keep the adapter's header
        // entry/position, but give it an empty slot so recycling cannot steal the bar.
        registerHeader.hookBefore { param ->
            val search = param.args[1] as? View ?: return@hookBefore
            if (param.args[0] == -1 && PinnedSearchBar.isNativeSearch(search)) {
                param.args[1] = PinnedSearchBar.messageSlot(search)
            }
        }

        createContacts.hookAfter { param ->
            val root = param.result as? View ?: return@hookAfter
            PinnedSearchBar.findSearch(root)?.let(PinnedSearchBar::contacts)
        }
    }
}
