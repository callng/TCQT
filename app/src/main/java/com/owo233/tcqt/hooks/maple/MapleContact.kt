package com.owo233.tcqt.hooks.maple

import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact as PC
import com.tencent.qqnt.kernel.nativeinterface.Contact as C

sealed class MapleContact(
    override val maple: Maple
): IMaple {
    companion object {
        fun from(instance: Any): MapleContact {
            return when (instance.javaClass.name) {
                "com.tencent.qqnt.kernelpublic.nativeinterface.Contact" -> PublicContact(instance)
                "com.tencent.qqnt.kernel.nativeinterface.Contact" -> Contact(instance)
                else -> throw IllegalArgumentException("Unknown instance type: ${instance.javaClass.name}")
            }
        }

        fun new(maple: Maple, chatType: Int, uid: String, subId: String = ""): MapleContact {
            if (chatType == MsgConstant.KCHATTYPEC2C && !uid.startsWith("u_")) {
                throw java.lang.IllegalArgumentException("uid must start with u_")
            }
            return if (maple == Maple.Kernel) {
                newV1(chatType, uid, subId)
            } else {
                newV2(chatType, uid, subId)
            }
        }

        // old nt api
        private fun newV1(chatType: Int, uid: String, subId: String): MapleContact {
            return Contact(C(chatType, uid, subId))
        }

        private fun newV2(chatType: Int, uid: String, subId: String): MapleContact {
            return PublicContact(PC(chatType, uid, subId))
        }
    }

    class PublicContact(
        instance: Any
    ): MapleContact(Maple.PublicKernel) {
        val inner: PC = instance as PC
    }

    class Contact(
        instance: Any
    ): MapleContact(Maple.Kernel) {
        val inner: C = instance as C
    }
}
