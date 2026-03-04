package com.owo233.tcqt.hooks.helper.message

import com.owo233.tcqt.utils.MethodHookParam
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass

interface MessageHandler {

    fun canHandle(msgType: Int, subType: Int): Boolean
    fun handleMsgPush(msgPush: MsgPushOuterClass.MsgPush, param: MethodHookParam)

    /**
     * 判断消息是否需要从 InfoSyncPush 中过滤掉
     * @return true 表示需要过滤（移除），false 表示保留
     */
    fun shouldFilterFromInfoSync(msg: QQMessageOuterClass.QQMessage): Boolean = false

    /**
     * 处理被过滤的消息（如显示提示）
     */
    fun onMessageFiltered(msg: QQMessageOuterClass.QQMessage) = Unit
}
