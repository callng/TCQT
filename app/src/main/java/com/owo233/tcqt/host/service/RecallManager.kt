package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.host.QQInterfaces
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
    private val recalledMessages = RecallEventHistory<MessageKey>(MAX_STORED_MESSAGES)
    private var loaded = false

    private val recalledMessagesKv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/anti_recall",
            "RecalledMessages"
        ).build()
    }

    /** Returns true only for the first observation, including across process restarts. */
    fun markC2C(peerUid: String, msgSeq: Long): Boolean =
        mark(MsgConstant.KCHATTYPEC2C, peerUid, msgSeq)

    fun markGroup(groupPeerId: String, msgSeq: Long): Boolean =
        mark(MsgConstant.KCHATTYPEGROUP, groupPeerId, msgSeq)

    fun isGroupRecalled(account: String, groupPeerId: String, msgSeq: Long): Boolean =
        account.isNotBlank() && msgSeq > 0 && isMessageRecalled(
            setOf(MessageKey(account, MsgConstant.KCHATTYPEGROUP, groupPeerId, msgSeq))
        )

    fun allowGroupRecall(account: String, groupPeerId: String, msgSeq: Long) = synchronized(this) {
        ensureLoaded()
        if (recalledMessages.remove(MessageKey(account, MsgConstant.KCHATTYPEGROUP, groupPeerId, msgSeq))) persist()
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
        recalledMessages.containsAny(keys)
    }

    fun addListener(listener: (MessageKey) -> Unit) {
        listeners.add(listener)
    }

    private fun mark(chatType: Int, peerId: String, msgSeq: Long): Boolean {
        if (peerId.isBlank() || msgSeq <= 0) return false

        val key = MessageKey(currentAccount(), chatType, peerId, msgSeq)
        if (key.account.isBlank()) return false

        val added = synchronized(this) {
            ensureLoaded()
            if (!recalledMessages.add(key)) return@synchronized false

            persist()
            true
        }

        if (added) listeners.forEach { listener -> runCatching { listener(key) } }
        return added
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
                ?.let(recalledMessages::restore)
        }
    }

    private fun persist() {
        recalledMessagesKv.putString(
            STORAGE_KEY,
            json.encodeToString(recalledMessages.snapshot())
        )
    }

    private fun currentAccount(): String = runCatching {
        QQInterfaces.currentUid.ifBlank { QQInterfaces.currentUin }
    }.getOrDefault("")
}
