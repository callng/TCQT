package com.owo233.tcqt.host.service

import androidx.annotation.Keep
import com.google.protobuf.CodedInputStream
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.env.launchWithCatch
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces

/** Reminders only. Native interception does not consult this class or its result. */
@Keep
internal object NativeRecallBridge {

    @JvmStatic private external fun nativeStart(): Boolean
    private val reminders = RecallPushHandler(GoogleRecallMetadataDecoder)

    fun start(): Boolean = NativeLibs.load("tcqtrecall") && nativeStart()

    @JvmStatic
    fun onNativeRecall(snapshot: ByteArray) {
        // Copy of the kernel's normalized RecallNotify, not an MSF push envelope.
        val input = CodedInputStream.newInstance(snapshot)
        var account = ""
        var chatType = 0
        var peer = ""
        var seq = 0L
        var operator = ""
        var sender = ""
        var id = 0L
        var clientSeq = 0L
        var time = 0L
        var random = 0L
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                10 -> account = input.readStringRequireUtf8()
                16 -> chatType = input.readUInt32()
                26 -> peer = input.readStringRequireUtf8()
                32 -> seq = input.readUInt64()
                42 -> operator = input.readStringRequireUtf8()
                50 -> sender = input.readStringRequireUtf8()
                56 -> id = input.readUInt64()
                64 -> clientSeq = input.readUInt64()
                72 -> time = input.readUInt64()
                80 -> random = input.readUInt64()
                else -> if (!input.skipField(tag)) break
            }
        }
        require(account.isNotBlank() && peer.isNotBlank() && operator.isNotBlank())
        val recall = RecallPacketParser.Recall(
            group = chatType == 2, peerId = peer, msgSeq = seq,
            operatorUid = operator, senderUid = sender, msgId = id,
            senderMsgSeq = clientSeq, msgTime = time, msgRandom = random,
            chatType = chatType,
        )
        // The kernel thread has already blocked this event. QQ service lookup,
        // persistence and UI work run asynchronously on the module's Java scope.
        ModuleScope.launchWithCatch {
            if (QQInterfaces.currentUid != account) {
                LogUtils.xposedNoFilter.w("NativeAntiRecall reminder skipped: account changed")
                return@launchWithCatch
            }
            LogUtils.xposedNoFilter.i("NativeAntiRecall event chatType=$chatType seq=$seq")
            reminders.notifyRecalls(listOf(recall))
        }
    }
}
