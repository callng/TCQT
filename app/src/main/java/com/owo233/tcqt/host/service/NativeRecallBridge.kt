package com.owo233.tcqt.host.service

import androidx.annotation.Keep
import com.google.protobuf.CodedInputStream
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.env.launchWithCatch
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Reminders only. Native interception does not consult this class or its result. */
@Keep
internal object NativeRecallBridge {

    @JvmStatic private external fun nativeStart(callbackClass: Class<*>, cacheFile: String): Boolean
    private val reminders = RecallPushHandler(GoogleRecallMetadataDecoder)

    private const val NATIVE_ENTRY_KEY = "tcqt.native_recall.entry.v1"
    private val started = AtomicBoolean(false)

    fun start(): Boolean {
        if (started.get()) return true
        // LSPosed replaces the module ClassLoader on hot reload. Keep the first
        // JNI owner alive for this process; a new loader only rebinds reminders.
        synchronized(NATIVE_ENTRY_KEY.intern()) {
            if (started.get()) return true
            val properties = System.getProperties()
            val entry = properties[NATIVE_ENTRY_KEY] as? Method ?: run {
                if (!NativeLibs.load("tcqtrecall")) return false
                NativeRecallBridge::class.java.getDeclaredMethod(
                    "nativeStart", Class::class.java, String::class.java
                ).apply {
                    isAccessible = true
                    // Loading and engine initialization have separate lifetimes:
                    // even a failed initialization must not load another SO copy.
                    properties[NATIVE_ENTRY_KEY] = this
                }
            }
            val ok = entry.invoke(
                null, NativeRecallBridge::class.java,
                HookEnv.application.cacheDir.resolve("tcqt-native-recall.bin").absolutePath
            ) as Boolean
            if (ok) started.set(true)
            return ok
        }
    }

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
                // LogUtils.xposedNoFilter.w("NativeAntiRecall reminder skipped: account changed")
                return@launchWithCatch
            }
            // LogUtils.xposedNoFilter.i("NativeAntiRecall event chatType=$chatType seq=$seq")
            reminders.notifyRecalls(listOf(recall))
        }
    }
}
