package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.service.ContactHelper
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.runBlocking

/**
 * QQ 号 / UID ↔ 宿主内核 `Contact` 的转换。
 *
 * 只负责"标识转换"，不涉及发送；发送在 [ScriptMessenger]。
 */
internal object ScriptContact {

    /** 由 QQ 号或 `u_` 开头的 UID 构造内核 Contact。 */
    fun contactOf(chatType: Int, peerUin: String): Contact = runBlocking {
        runCatching {
            val maple = if (peerUin.startsWith("u_")) {
                ContactHelper.generateContactByUid(chatType, peerUin)
            } else {
                ContactHelper.generateContact(chatType, peerUin)
            }
            maple.publicContact()
        }.getOrElse {
            LogUtils.androidNoFilter.e("脚本 Contact 构造失败: chatType=$chatType, peer=$peerUin", it)
            Contact(chatType, peerUin, "")
        }
    }

    /** 只做"QQ 号 / UID → 内核标识"的转换。 */
    fun uidOf(chatType: Int, peerUin: String): String {
        if (peerUin.startsWith("u_")) return peerUin
        return runBlocking {
            runCatching { ContactHelper.generateContact(chatType, peerUin).publicContact().peerUid }
                .getOrDefault(peerUin)
        }
    }

    private fun com.owo233.tcqt.host.service.maple.MapleContact.publicContact(): Contact =
        when (this) {
            is com.owo233.tcqt.host.service.maple.MapleContact.PublicContact -> inner
            is com.owo233.tcqt.host.service.maple.MapleContact.Contact ->
                Contact(inner.chatType, inner.peerUid, inner.guildId ?: "")
        }
}
