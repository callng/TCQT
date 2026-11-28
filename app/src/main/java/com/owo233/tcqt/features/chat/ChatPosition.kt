package com.owo233.tcqt.features.chat

/** Unread accounting survives reentry; the return viewport belongs to this visit. */
internal class ChatReadingProgress(readSeq: Long) {

    var readSeq = readSeq
        private set
    var furthestVisibleSeq = 0L
        private set
    private var bottomSeq = 0L
    private var resume: ChatPosition? = null

    fun onVisible(bottom: ChatPosition, first: ChatPosition?) {
        bottomSeq = bottom.msgSeq
        readSeq = maxOf(readSeq, bottomSeq)
        if (bottomSeq >= furthestVisibleSeq) {
            furthestVisibleSeq = bottomSeq
            resume = first?.takeIf { it.msgSeq > 0L } ?: bottom
        }
    }

    fun returnTarget(latestSeq: Long): ChatPosition? =
        resume?.takeIf { bottomSeq in 1 until furthestVisibleSeq && readSeq < latestSeq }

    fun onReturned() {
        resume = null
    }
}

internal data class ChatPosition(
    val msgId: Long,
    val msgSeq: Long,
    val offset: Int,
    val readSeq: Long = 0L
) {

    fun encode(): String = "$msgId,$msgSeq,$offset,$readSeq"

    fun matchesNavigation(id: Long, seq: Long, time: Long): Boolean =
        time <= 0L && (id > 0L || seq > 0L) &&
            (id <= 0L || id == msgId) && (seq <= 0L || seq == msgSeq)

    fun indexIn(messages: List<Pair<Long, Long>>): Int {
        if (msgId != 0L) {
            val exact = messages.indexOfFirst { it.first == msgId }
            if (exact >= 0) return exact
        }
        if (msgSeq <= 0L) return -1
        // 同一 seq 可能对应多个条目，只有唯一匹配时才允许脱离本地 msgId 定位。
        val first = messages.indexOfFirst { it.second == msgSeq }
        return if (first >= 0 && messages.indexOfLast { it.second == msgSeq } == first) first else -1
    }

    companion object {
        fun unreadCount(readSeq: Long, latestSeq: Long): Long =
            if (readSeq > 0L) (latestSeq - readSeq).coerceAtLeast(0L) else 0L

        fun decode(value: String): ChatPosition? {
            val parts = value.split(',')
            if (parts.size !in 3..4) return null
            val id = parts[0].toLongOrNull() ?: return null
            val seq = parts[1].toLongOrNull() ?: return null
            val offset = parts[2].toIntOrNull() ?: return null
            val readSeq = if (parts.size == 4) parts[3].toLongOrNull()?.takeIf { it >= 0L } ?: return null else 0L
            if (id <= 0L && seq <= 0L) return null
            return ChatPosition(id, seq, offset, readSeq)
        }

        fun conversationKey(account: String, chatType: Int, peerUid: String): String? {
            if (account.isEmpty() || peerUid.isEmpty() || chatType !in 1..2) return null
            return "$account:$chatType:$peerUid"
        }
    }
}
