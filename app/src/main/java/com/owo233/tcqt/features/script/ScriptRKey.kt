package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.group.RKeyCollector

/**
 * 图片 rkey（脚本 `getGroupRKey()` / `getFriendRKey()`）。
 *
 * rkey 由宿主向服务端换取，没有一次性获取接口，只能从宿主收到的响应里采集，
 * 采集逻辑在 core 的 [RKeyCollector]。这里只做读取。
 *
 * 取不到时返回空串 —— 脚本拿到空串就不会拼出无效的图片地址。
 */
internal object ScriptRKey {

    fun group(): String = RKeyCollector.groupRkey

    fun friend(): String = RKeyCollector.friendRkey
}
