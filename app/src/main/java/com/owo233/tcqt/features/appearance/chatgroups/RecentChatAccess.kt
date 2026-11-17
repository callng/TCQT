package com.owo233.tcqt.features.appearance.chatgroups

import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.hostFunction
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.qqnt.aio.adapter.api.impl.GuildMsgApiImpl
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo

/** Native contact fields are shared by QQ 9.3.x; older lists have no fold wrapper. */
internal class RecentChatAccess(private val itemClass: Class<*>, foldClass: Class<*>?) {
    private val contact = itemClass.declaredFields.single { it.type == RecentContactInfo::class.java }
        .apply { isAccessible = true }
    private val folded = foldClass?.declaredFields?.single { it.type == List::class.java }
        ?.apply { isAccessible = true }

    fun isConversation(item: Any): Boolean = itemClass.isInstance(item)

    fun describe(item: Any): ChatSummary? {
        if (!itemClass.isInstance(item)) return null
        return describe(contact.get(item) as RecentContactInfo)
    }

    private fun describe(info: RecentContactInfo): ChatSummary {
        val guild = info.guildContactInfo
        val guildUnread = guild?.unreadCntInfo?.showUnreadCnt
        return ChatSummary(
            key = "${info.chatType}:${info.contactId}",
            title = info.remark.orEmpty().ifBlank { info.peerName.orEmpty() }
                .ifBlank { guild?.guildName.orEmpty() }
                .ifBlank { info.peerUid.orEmpty() }.ifBlank { info.contactId.toString() },
            type = info.chatType,
            unread = info.unreadCnt > 0 || info.unreadFlag == 1L ||
                (info.chatType == 16 && guildUnread != null && guildUnread.type != 4 && guildUnread.cnt > 0),
            detail = listOfNotNull(when (info.chatType) {
                1 -> "好友"
                2 -> "群聊"
                16, 60 -> "频道"
                100 -> "临时会话"
                else -> "其他会话"
            }, info.peerUin.takeIf { it > 0 }?.toString()).joinToString(" · "),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun foldedItems(item: Any): List<Any>? =
        if (folded != null && folded.declaringClass.isInstance(item)) folded.get(item) as List<Any> else null

    private fun contacts(items: List<Any>): List<RecentContactInfo> = buildMap {
        fun append(rows: List<Any>) {
            for (item in rows) {
                val children = foldedItems(item)
                if (children != null) append(children)
                else if (itemClass.isInstance(item)) {
                    val info = contact.get(item) as RecentContactInfo
                    put("${info.chatType}:${info.contactId}", info)
                }
            }
        }
        append(items)
    }.values.toList()

    fun catalog(items: List<Any>): List<ChatSummary> = contacts(items).map { describe(it) }

    fun readTargets(
        items: List<Any>, id: String, groups: List<CustomChatGroup>,
        troopHelperContacts: List<RecentContactInfo>? = null,
    ): List<RecentContactInfo> = contacts(items).filter {
        val chat = describe(it)
        chat.unread && matchesChatGroup(id, chat, groups)
    }.flatMap { info ->
        if (info.chatType == 7 && troopHelperContacts != null) {
            troopHelperContacts.filter { it.chatType == 2 && describe(it).unread }
        } else listOf(info)
    }.distinctBy { "${it.chatType}:${it.contactId}" }

    fun markRead(items: List<Any>, id: String, groups: List<CustomChatGroup>, onFinished: (String?) -> Unit) {
        val targets = readTargets(items, id, groups)
        if (targets.isEmpty()) return
        val service = QQInterfaces.msgService
        val main = Handler(Looper.getMainLooper())
        fun mark(targets: List<RecentContactInfo>) {
            if (targets.isEmpty()) {
                onFinished(null)
                return
            }
            var remaining = targets.size
            var firstError: String? = null
            for (info in targets) {
                val callback = IOperateCallback { code, message ->
                    main.post {
                        if (code != 0) {
                            Log.e("ChatGroups mark read failed: type=${info.chatType}, code=$code, message=$message")
                            firstError = "部分会话标记已读失败（$code）"
                        }
                        remaining--
                        if (remaining == 0) onFinished(firstError)
                    }
                }
                if (info.chatType == 16 || info.chatType == 60 || info.chatType == 62) {
                    // QQ's GuildRedDotDragUseCase clears the guild through this adapter,
                    // including the guild-message folder (60), rather than setMsgRead.
                    GuildMsgApiImpl::class.java.findMethod {
                        name = "setGProGuildMsgRead"
                        paramTypes(string, int, "kotlin.jvm.functions.Function2".toClass)
                    }.also { method ->
                        method.invoke(
                            GuildMsgApiImpl(),
                            info.peerUid,
                            info.chatType,
                            hostFunction<Int, String>(method.parameterTypes[2]) { c, m ->
                                callback.onResult(c, m)
                            }
                        )
                    }
                } else {
                    when (val target = MapleContact.new(QQInterfaces.maple, info.chatType, info.peerUid)) {
                        is MapleContact.Contact -> service.setMsgRead(target.inner, callback)
                        is MapleContact.PublicContact -> service.setMsgRead(target.inner, callback)
                    }
                }
            }
        }
        if (targets.any { it.chatType == 7 }) {
            loadTroopHelperContacts { code, message, contacts ->
                if (code != 0) {
                    Log.e("ChatGroups group assistant load failed: code=$code, message=$message")
                    onFinished("群助手会话读取失败（$code）")
                } else mark(readTargets(items, id, groups, contacts))
            }
        } else mark(targets)
    }
}
