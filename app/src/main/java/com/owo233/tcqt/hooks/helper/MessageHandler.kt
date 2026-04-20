package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.utils.hook.MethodHookParam

interface MessageHandler {
    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam)
    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam)
}
