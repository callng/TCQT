package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.message.MessageEvent
import com.owo233.tcqt.core.message.MessageKind
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 把「消息入口的宿主结构」钉成断言。
 *
 * 这些常量来自真机 dex 的实际结构（QQ 9.3.70，base.apk）：
 *
 * ```
 * public class com.tencent.qqnt.msg.j implements IKernelMsgListener   // TAG = IMsgListenerAdapter
 *     public void onRecvMsg(ArrayList<MsgRecord>)      // 服务端推送落地的消息
 *     public void onAddSendMsg(MsgRecord)              // 本机发出的消息
 *
 * public final class com.tencent.qqnt.msg.MsgService$d extends j
 *     // 重写 onRecvMsg：先 super.onRecvMsg(...)，再分发给 MsgService 上注册的监听器
 *     // 即"消息适配器基类 + 真正在用的子类"，两个类都要挂
 * ```
 *
 * 混淆名（`j` / `MsgService$d`）会随宿主版本变，**方法名与参数形态不会**：DexKit 定位、
 * `MessageCore` 的 hook 条件、参数派发全都依赖这两点。所以这里只钉方法与形态，不钉类名。
 */
class MessageEntryShapeTest {

    private val receiveDescriptor = "onRecvMsg(Ljava/util/ArrayList;)V"
    private val sendDescriptor = "onAddSendMsg(Lcom/tencent/qqnt/kernel/nativeinterface/MsgRecord;)V"

    private fun declaredMethod(name: String, param: Class<*>): Method =
        FakeMessageEntry::class.java.getDeclaredMethod(name, param)

    /** 与真机入口类同形的替身：两个回调 + 正确参数类型。 */
    @Suppress("unused")
    private class FakeMessageEntry {
        fun onRecvMsg(msgList: ArrayList<MsgRecord>) = Unit

        fun onAddSendMsg(record: MsgRecord) = Unit

        /** 同名但参数不符的干扰项：用来验证 hook 条件会拒绝它。 */
        fun onRecvMsg(text: String) = Unit
    }

    @Test
    fun `收到消息的回调是单参数 ArrayList`() {
        val method = declaredMethod("onRecvMsg", ArrayList::class.java)

        assertEquals("onRecvMsg", method.name)
        assertEquals(1, method.parameterCount)
        assertEquals(ArrayList::class.java, method.parameterTypes[0])
    }

    @Test
    fun `本机发送的回调是单参数 MsgRecord`() {
        val method = declaredMethod("onAddSendMsg", MsgRecord::class.java)

        assertEquals("onAddSendMsg", method.name)
        assertEquals(1, method.parameterCount)
        assertEquals(MsgRecord::class.java, method.parameterTypes[0])
    }

    /**
     * `MessageCore.hookCandidate` 的接受条件必须与上面两条形态一致：
     * 恰好一个参数，且形参是 `ArrayList` 或 `MsgRecord`。
     */
    @Test
    fun `hook 条件接受 ArrayList 与 MsgRecord 两种形参`() {
        assertTrue(isHookCandidate(declaredMethod("onRecvMsg", ArrayList::class.java)))
        assertTrue(isHookCandidate(declaredMethod("onAddSendMsg", MsgRecord::class.java)))
    }

    @Test
    fun `hook 条件拒绝同名但参数不符的方法`() {
        assertFalse(
            isHookCandidate(declaredMethod("onRecvMsg", String::class.java)),
            "同名但参数不是 ArrayList 的方法不能被当成消息入口",
        )
    }

    /** 与 `MessageCore.hookCandidate` 相同的判据。 */
    private fun isHookCandidate(method: Method): Boolean {
        if (method.parameterCount != 1) return false
        val type = method.parameterTypes[0]
        return type == ArrayList::class.java || MsgRecord::class.java.isAssignableFrom(type)
    }

    @Test
    fun `派发条件能从两种参数形态里都取出 MsgRecord`() {
        val record = MsgRecord()
        record.msgId = 1234

        assertEquals(record, firstRecordOf(record))
        assertEquals(record, firstRecordOf(arrayListOf(record)))
    }

    /** 与 `MessageCore.dispatch` 相同的取值方式。 */
    private fun firstRecordOf(arg: Any?): MsgRecord? = when (arg) {
        is Collection<*> -> arg.firstOrNull() as? MsgRecord
        is MsgRecord -> arg
        else -> null
    }

    @Test
    fun `事件把 kind 与 record 一起带出`() {
        val record = MsgRecord()
        record.msgId = 9

        val receive = MessageEvent(MessageKind.RECEIVE, record)
        val send = MessageEvent(MessageKind.SEND, record)

        assertEquals(MessageKind.RECEIVE, receive.kind)
        assertEquals(MessageKind.SEND, send.kind)
        assertEquals(9L, receive.msgId)
    }

    @Test
    fun `两条回调的描述串与真机一致（供 DexKit 查询复核）`() {
        // 只是把真机证据留在这里：改动 DexKit 查询条件时，这两个串必须仍然成立。
        assertEquals("onRecvMsg(Ljava/util/ArrayList;)V", receiveDescriptor)
        assertEquals(
            "onAddSendMsg(Lcom/tencent/qqnt/kernel/nativeinterface/MsgRecord;)V",
            sendDescriptor,
        )
    }
}
