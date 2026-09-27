package com.owo233.tcqt.features.script.bean

/** 脚本可见的禁言信息。 */
data class ForbidInfo(
    @JvmField val user: String,
    @JvmField val userName: String,
    /** 剩余禁言秒数。 */
    @JvmField val time: Long,
    /** 禁言结束时间戳（秒）。 */
    @JvmField val endTime: Long,
)
