package com.owo233.tcqt.features.script.bean

/** 脚本可见的好友信息。 */
data class FriendInfo(
    @JvmField val uin: String,
    @JvmField val uid: String,
    @JvmField val name: String,
    @JvmField val remark: String,
)
