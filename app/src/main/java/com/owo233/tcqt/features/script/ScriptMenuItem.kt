package com.owo233.tcqt.features.script

/**
 * 脚本悬浮菜单的条目模型。
 *
 * 菜单按脚本分组：每个运行中且注册过 `addItem` 的脚本产生一个 [Header]，
 * 其下是若干 [Action]。
 */
internal sealed class ScriptMenuItem {

    /** 脚本分组标题。 */
    data class Header(val scriptName: String) : ScriptMenuItem()

    /** 可点击的脚本菜单项。 */
    data class Action(
        val scriptId: String,
        val title: String,
        val callback: String,
    ) : ScriptMenuItem()
}

/**
 * 聊天界面上下文：`chatInterface` 回调与脚本菜单回调都传它。
 *
 * [peerUin] 对群聊就是群号；对好友是转换后的 QQ 号（可能为空，取决于宿主是否给得出）。
 */
internal data class ScriptChatContext(
    val chatType: Int = 0,
    val peerUid: String = "",
    val peerUin: String = "",
    val guildId: String = "",
    val peerName: String = "",
) {

    val isValid: Boolean get() = chatType != 0

    /** 转成宿主内核 `Contact`，供脚本菜单回调使用。 */
    fun toKernelContact(): com.tencent.qqnt.kernelpublic.nativeinterface.Contact =
        com.tencent.qqnt.kernelpublic.nativeinterface.Contact(chatType, peerUid, guildId)
}
