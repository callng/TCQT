package com.owo233.tcqt.features.script.bean

import com.tencent.mobileqq.data.troop.TroopInfo

/** 脚本可见的群信息，[groupInfo] 为宿主原始对象。 */
data class GroupInfo(
    @JvmField val group: String,
    @JvmField val groupName: String,
    @JvmField val groupOwner: String,
    @JvmField val groupInfo: TroopInfo?,
)
