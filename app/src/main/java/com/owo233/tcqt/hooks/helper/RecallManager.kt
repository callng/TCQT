package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.internals.QQInterfaces
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import io.fastkv.FastKV
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

internal object RecallManager {

    @Serializable
    data class MessageKey(
        val account: String,
        val chatType: Int,
        val peerId: String,
        val msgSeq: Long
    )

    private const val STORAGE_KEY = "messages"
    private const val MAX_STORED_MESSAGES = 2048

    private val json = Json { ignoreUnknownKeys = true }
    private val listeners = CopyOnWriteArrayList<(MessageKey) -> Unit>()
    private val recalledMessages = LinkedHashSet<MessageKey>()
    private var loaded = false

    private val recalledMessagesKv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/anti_recall",
            "RecalledMessages"
        ).build()
    }

    fun markC2C(peerUid: String, msgSeq: Long) {
        mark(MsgConstant.KCHATTYPEC2C, peerUid, msgSeq)
    }

    fun markGroup(groupPeerId: String, msgSeq: Long) {
        mark(MsgConstant.KCHATTYPEGROUP, groupPeerId, msgSeq)
    }

    fun keysOf(msg: MsgRecord): Set<MessageKey> {
        if (msg.msgSeq <= 0) return emptySet()

        val account = currentAccount()
        if (account.isBlank()) return emptySet()

        val peerIds = linkedSetOf<String>()
        msg.peerUid.takeIf { it.isNotBlank() }?.let(peerIds::add)
        msg.peerUin.takeIf { it > 0 }?.toString()?.let(peerIds::add)

        if (msg.chatType == MsgConstant.KCHATTYPEC2C) {
            msg.senderUid
                .takeIf { it.isNotBlank() && it != account }
                ?.let(peerIds::add)
        }

        return peerIds.mapTo(linkedSetOf()) { peerId ->
            MessageKey(account, msg.chatType, peerId, msg.msgSeq)
        }
    }

    fun isMessageRecalled(keys: Set<MessageKey>): Boolean = synchronized(this) {
        ensureLoaded()
        keys.any(recalledMessages::contains)
    }

    fun addListener(listener: (MessageKey) -> Unit) {
        listeners.add(listener)
    }

    private fun mark(chatType: Int, peerId: String, msgSeq: Long) {
        if (peerId.isBlank() || msgSeq <= 0) return

        val key = MessageKey(currentAccount(), chatType, peerId, msgSeq)
        if (key.account.isBlank()) return

        val added = synchronized(this) {
            ensureLoaded()
            if (!recalledMessages.add(key)) return@synchronized false

            while (recalledMessages.size > MAX_STORED_MESSAGES) {
                val oldest = recalledMessages.firstOrNull() ?: break
                recalledMessages.remove(oldest)
            }
            persist()
            true
        }

        if (added) listeners.forEach { listener -> runCatching { listener(key) } }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true

        val stored = recalledMessagesKv.getString(STORAGE_KEY, null).orEmpty()

        if (stored.isNotBlank()) {
            runCatching {
                json.decodeFromString<List<MessageKey>>(stored)
            }.getOrNull()
                ?.takeLast(MAX_STORED_MESSAGES)
                ?.let(recalledMessages::addAll)
        }
    }

    private fun persist() {
        recalledMessagesKv.putString(
            STORAGE_KEY,
            json.encodeToString(recalledMessages.toList())
        )
    }

    private fun currentAccount(): String = runCatching {
        QQInterfaces.currentUid.ifBlank { QQInterfaces.currentUin }
    }.getOrDefault("")
}
