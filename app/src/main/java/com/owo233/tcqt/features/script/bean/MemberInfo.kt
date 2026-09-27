package com.owo233.tcqt.features.script.bean

/**
 * 脚本可见的群成员信息。
 *
 * 字段全部为 `@JvmField`：BeanShell 以 `成员.uin` 的形式直接读字段，
 * 不经过 getter，混淆时也必须保留（见 `libs/beanshell` 的 keep 规则要求）。
 */
data class MemberInfo(
    @JvmField val uin: String,
    @JvmField val uinName: String,
    @JvmField val uinLevel: Int,
    @JvmField val joinGroupTime: Long,
    @JvmField val lastActiveTime: Long,
    @JvmField val role: String,
    /** 禁言结束时间戳（秒）；0 或已过去表示未禁言。 */
    @JvmField val shutUpEndTime: Long,
    @JvmField val memberInfo: Any?,
)
