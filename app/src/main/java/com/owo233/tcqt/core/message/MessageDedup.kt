package com.owo233.tcqt.core.message

/**
 * 消息去重窗口（有界 LRU 语义的集合）。
 *
 * 内核可能对同一条消息多次回调：多个内核实例、重连重放、`onRecvMsg` 与
 * `onAddSendMsg` 同时命中同一条。这里只保留最近 [limit] 个已处理 id，
 * 超出后按进入顺序淘汰最旧的。
 *
 * 单独抽出来是为了能被单测直接覆盖：去重一旦失效，脚本会对同一条消息重复执行。
 */
internal class MessageDedup(private val limit: Int = DEFAULT_LIMIT) {

    private val seen = LinkedHashSet<Long>()

    /**
     * 尝试占用一个 id。
     *
     * @return true 表示首次见到（应当继续分发）；false 表示重复（应当丢弃）。
     */
    fun tryMark(id: Long): Boolean = synchronized(seen) {
        if (!seen.add(id)) return false

        if (seen.size > limit) {
            val iterator = seen.iterator()
            repeat(seen.size - limit) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        return true
    }

    fun clear() = synchronized(seen) { seen.clear() }

    val size: Int get() = synchronized(seen) { seen.size }

    companion object {
        const val DEFAULT_LIMIT = 2048
    }
}
