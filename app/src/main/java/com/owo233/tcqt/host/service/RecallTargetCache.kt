package com.owo233.tcqt.host.service

/** Recent AIO identity snapshots only; never retains message text, views or mutable MsgRecords. */
internal class RecallTargetCache(
    private val capacity: Int = 512,
    private val ttlNanos: Long = 300_000_000_000L,
    private val clock: () -> Long = System::nanoTime,
) {
    private data class Key(val account: String, val group: Boolean, val peerId: String, val seq: Long)
    private data class Entry(val target: RecallTargetLookup.Target, val seenAt: Long)
    private val entries = LinkedHashMap<Key, Entry>()

    init {
        require(capacity > 0 && ttlNanos > 0)
    }

    @Synchronized
    fun remember(account: String, group: Boolean, peerId: String, target: RecallTargetLookup.Target) {
        if (account.isBlank() || peerId.isBlank() || target.msgSeq <= 0 ||
            !target.originalMessage || target.senderUid.isBlank()) return
        val key = Key(account, group, peerId, target.msgSeq)
        entries.remove(key)
        entries[key] = Entry(target, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun find(account: String, recall: RecallPacketParser.Recall): RecallTargetLookup.Target? {
        val now = clock()
        entries.entries.removeAll { now - it.value.seenAt >= ttlNanos }
        return entries.entries.lastOrNull { (key, entry) ->
            key.account == account && key.group == recall.group && key.peerId == recall.peerId &&
                RecallTargetLookup.matches(recall, entry.target)
        }?.value?.target
    }
}
