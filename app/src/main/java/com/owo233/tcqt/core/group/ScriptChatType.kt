package com.owo233.tcqt.core.group

/**
 * 会话类型取值。
 *
 * 与脚本 API 文档一致，也与宿主 `MsgConstant` 的取值一致；放在 core 是因为
 * 群事件解析（core 层）要用到，而 core 不能反向依赖 features。
 */
object ScriptChatType {

    /** 好友 / 私聊。 */
    const val FRIEND = 1

    /** 群聊。 */
    const val GROUP = 2

    /** 陌生人。 */
    const val STRANGER = 100
}
