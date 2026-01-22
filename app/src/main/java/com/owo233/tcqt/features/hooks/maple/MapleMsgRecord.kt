package com.owo233.tcqt.features.hooks.maple

import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord as MR1

sealed class MapleMsgRecord(
    override val maple: Maple
): IMaple {
    companion object {
        fun from(instance: Any): MapleMsgRecord {
            return if (instance.javaClass.name[23] == 'p') {
                PublicMsgRecord(instance)
            } else if (instance.javaClass.name[23] == '.') {
                KernelMsgRecord(instance)
            } else {
                throw IllegalArgumentException("Unknown MsgRecord type: ${instance.javaClass.name}")
            }
        }
    }

    abstract val msgType: Int
    abstract val chatType: Int
    abstract val peerUid: String
    abstract val elements: ArrayList<MsgElement>

    abstract fun isEmpty(): Boolean

    class KernelMsgRecord(
        instance: Any
    ): MapleMsgRecord(Maple.Kernel) {
        private val innerRecord = instance as MR1

        override val msgType: Int
            get() = innerRecord.msgType

        override val chatType: Int
            get() = innerRecord.chatType

        override val peerUid: String
            get() = innerRecord.peerUid
        override val elements: ArrayList<MsgElement>
            get() = innerRecord.elements

        override fun isEmpty() = innerRecord.elements?.isEmpty() ?: true
    }

    class PublicMsgRecord(
        instance: Any
    ): MapleMsgRecord(Maple.PublicKernel) {
        override val msgType: Int
            get() = error("Not implemented")

        override val chatType: Int
            get() = error("Not implemented")
        override val peerUid: String
            get() = error("Not implemented")
        override val elements: ArrayList<MsgElement>
            get() = error("Not implemented")

        override fun isEmpty() = error("Not implemented")
    }
}
