package com.owo233.tcqt.features.script

import android.app.Activity
import com.owo233.tcqt.core.env.CalculationUtils
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.script.bean.ForbidInfo
import com.owo233.tcqt.features.script.bean.FriendInfo
import com.owo233.tcqt.features.script.bean.GroupInfo
import com.owo233.tcqt.features.script.bean.MemberInfo
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.TicketManager
import com.owo233.tcqt.host.service.api.GroupService
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import com.tencent.qqnt.ntrelation.friendsinfo.api.IFriendsInfoService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 脚本 API：注入解释器命名空间的全局方法集合。
 *
 * [ScriptRuntime.bindApiMethods] 反射注入全部公开实例方法，因此新增 API
 * 只需在本类加方法，签名必须与脚本文档一致。
 *
 * 约定：单个 API 失败只记录日志并返回默认值，不打断脚本执行。
 */
@Suppress("unused")
internal class ScriptNamespace(private val runtime: ScriptRuntime) {

    // ── 日志与提示 ───────────────────────────────────────────────────────

    @JvmOverloads
    fun log(msg: Any?, fileName: String = "log.txt") {
        runCatching {
            File(runtime.info.dirPath, fileName)
                .appendText("${timestamp()} $msg\n")
        }.onFailure { Log.e("脚本日志写入失败 [${runtime.info.id}]", it) }
    }

    fun toast(msg: Any?) {
        Toasts.info(msg.toString())
    }

    /** [icon]：0 提示 / 1 失败 / 2 成功。 */
    fun qqToast(icon: Int, msg: Any?) {
        when (icon) {
            1 -> Toasts.error(msg.toString())
            2 -> Toasts.success(msg.toString())
            else -> Toasts.info(msg.toString())
        }
    }

    fun getNowActivity(): Activity? = runtime.currentActivity()

    // ── 菜单注册 ─────────────────────────────────────────────────────────

    /** 注册脚本菜单项，[callback] 为脚本里 3 参或 4 参的方法名。 */
    fun addItem(name: String, callback: String) {
        runtime.menuItems[name] = callback
    }

    /** 注册消息长按菜单项；[msgTypes] 为空表示所有消息类型。 */
    @JvmOverloads
    fun addMenuItem(name: String, callback: String, msgTypes: IntArray = intArrayOf()) {
        runtime.msgMenuItems[name] = callback
    }

    // ── 数据存储（脚本目录 config/*.json） ───────────────────────────────

    fun putString(configName: String, key: String, value: String) =
        ScriptConfig.put(runtime.configDir, configName, key, value)

    fun putInt(configName: String, key: String, value: Int) =
        ScriptConfig.put(runtime.configDir, configName, key, value)

    fun putLong(configName: String, key: String, value: Long) =
        ScriptConfig.put(runtime.configDir, configName, key, value)

    fun putBoolean(configName: String, key: String, value: Boolean) =
        ScriptConfig.put(runtime.configDir, configName, key, value)

    fun getString(configName: String, key: String, defaultValue: String): String =
        ScriptConfig.get(runtime.configDir, configName, key, defaultValue)

    fun getInt(configName: String, key: String, defaultValue: Int): Int =
        ScriptConfig.get(runtime.configDir, configName, key, defaultValue)

    fun getLong(configName: String, key: String, defaultValue: Long): Long =
        ScriptConfig.get(runtime.configDir, configName, key, defaultValue)

    fun getBoolean(configName: String, key: String, defaultValue: Boolean): Boolean =
        ScriptConfig.get(runtime.configDir, configName, key, defaultValue)

    // ── 消息 ─────────────────────────────────────────────────────────────

    fun sendMsg(peerUin: String, msg: String, chatType: Int) =
        ScriptMessenger.sendText(chatType, peerUin, msg)

    fun sendMsg(contact: Contact, msg: String) =
        ScriptMessenger.sendText(contact, msg)

    fun sendPic(peerUin: String, path: String, chatType: Int) =
        ScriptMessenger.sendElement(chatType, peerUin, "图片") { it.picElement = ScriptMessenger.pic(path) }

    fun sendPic(contact: Contact, path: String) =
        ScriptMessenger.sendElement(contact, "图片") { it.picElement = ScriptMessenger.pic(path) }

    fun sendPtt(peerUin: String, path: String, chatType: Int) =
        sendPtt(peerUin, path, chatType, 0)

    fun sendPtt(contact: Contact, path: String) =
        sendPtt(contact, path, 0)

    /** [durationMs] 毫秒；不大于 0 时按 1 秒发送。 */
    fun sendPtt(peerUin: String, path: String, chatType: Int, durationMs: Int) =
        ScriptMessenger.sendElement(chatType, peerUin, "语音") {
            it.pttElement = ScriptMessenger.ptt(path, durationMs)
        }

    fun sendPtt(contact: Contact, path: String, durationMs: Int) =
        ScriptMessenger.sendElement(contact, "语音") {
            it.pttElement = ScriptMessenger.ptt(path, durationMs)
        }

    fun sendFile(peerUin: String, path: String, chatType: Int) =
        ScriptMessenger.sendElement(chatType, peerUin, "文件") { it.fileElement = ScriptMessenger.file(path) }

    fun sendFile(contact: Contact, path: String) =
        ScriptMessenger.sendElement(contact, "文件") { it.fileElement = ScriptMessenger.file(path) }

    fun sendVideo(peerUin: String, path: String, chatType: Int) =
        ScriptMessenger.sendElement(chatType, peerUin, "视频") { it.videoElement = ScriptMessenger.video(path) }

    fun sendVideo(contact: Contact, path: String) =
        ScriptMessenger.sendElement(contact, "视频") { it.videoElement = ScriptMessenger.video(path) }

    fun sendCard(peerUin: String, data: String, chatType: Int) =
        ScriptMessenger.sendElement(chatType, peerUin, "卡片") { it.arkElement = ScriptMessenger.ark(data) }

    fun sendCard(contact: Contact, data: String) =
        ScriptMessenger.sendElement(contact, "卡片") { it.arkElement = ScriptMessenger.ark(data) }

    fun sendReplyMsg(peerUin: String, replyMsgId: Long, msg: String, chatType: Int) =
        ScriptMessenger.sendReply(chatType, peerUin, replyMsgId, msg)

    fun sendReplyMsg(contact: Contact, replyMsgId: Long, msg: String) =
        ScriptMessenger.sendReply(contact, replyMsgId, msg)

    fun recallMsg(chatType: Int, peerUin: String, msgId: Long) =
        ScriptMessenger.recall(chatType, peerUin, msgId)

    fun recallMsg(contact: Contact, msgId: Long) =
        ScriptMessenger.recall(contact, msgId)

    fun sendPai(toUin: String, peerUin: String, chatType: Int) =
        ScriptMessenger.sendPai(chatType, peerUin, toUin)

    // ── 好友 ─────────────────────────────────────────────────────────────

    fun isFriend(uin: String): Boolean = guard("isFriend", false) {
        QQInterfaces.api<IFriendsInfoService>().isFriend(QQInterfaces.currentUid, uin)
    }

    fun getUidFromUin(uin: String): String =
        guard("getUidFromUin", "") { GroupService.getUidFromUin(uin) }

    fun getUinFromUid(uid: String): String =
        guard("getUinFromUid", "") { GroupService.getUinFromUid(uid) }

    /** 宿主未暴露好友列表接口，恒返回空列表。 */
    fun getAllFriend(): List<FriendInfo> {
        unsupported("getAllFriend")
        return emptyList()
    }

    fun sendZan(uin: String, count: Int) = unsupported("sendZan(uin=$uin, count=$count)")

    // ── 群 ───────────────────────────────────────────────────────────────

    /** 宿主未暴露群列表接口，恒返回空列表。 */
    fun getGroupList(): List<GroupInfo> {
        unsupported("getGroupList")
        return emptyList()
    }

    fun getGroupInfo(groupUin: String): Any? =
        guard("getGroupInfo", null) { GroupService.getGroupInfo(groupUin) }

    /** 宿主未暴露群成员列表接口，恒返回空列表。 */
    fun getGroupMemberList(groupUin: String): List<MemberInfo> {
        unsupported("getGroupMemberList(group=$groupUin)")
        return emptyList()
    }

    fun getMemberInfo(groupUin: String, uin: String): MemberInfo? =
        getGroupMemberList(groupUin).firstOrNull { it.uin == uin }

    /** 宿主未暴露禁言列表接口，恒返回空列表。 */
    fun getProhibitList(groupUin: String): List<ForbidInfo> {
        unsupported("getProhibitList(group=$groupUin)")
        return emptyList()
    }

    /**
     * 群是否处于全员禁言。
     * （`dwGagTimeStamp` 为全员禁言，`dwGagTimeStamp_me` 为只禁我）。
     */
    fun isShutUp(groupUin: String): Boolean = guard("isShutUp", false) {
        val info = GroupService.getGroupInfo(groupUin)
        !(info.dwGagTimeStamp == 0L && info.dwGagTimeStamp_me == 0L)
    }

    fun shutUp(groupUin: String, uin: String, seconds: Long) =
        GroupService.setMemberShutUp(groupUin, uin, seconds.toInt())

    fun shutUpAll(groupUin: String, enable: Boolean) =
        GroupService.setGroupShutUp(groupUin, enable)

    fun kickGroup(groupUin: String, uin: String, block: Boolean) =
        GroupService.kickMember(groupUin, uin, block)

    fun setGroupAdmin(groupUin: String, uin: String, enable: Boolean) =
        GroupService.modifyMemberRole(groupUin, uin, enable)

    fun setGroupMemberTitle(groupUin: String, uin: String, title: String) =
        GroupService.setMemberTitle(groupUin, uin, title)

    fun changeMemberName(groupUin: String, uin: String, newName: String) =
        GroupService.modifyMemberCardName(groupUin, uin, newName)

    fun clockIn(groupUin: String) = unsupported("clockIn(group=$groupUin)")

    // ── 票据 ─────────────────────────────────────────────────────────────

    fun getSkey(): String = guard("getSkey", "") { TicketManager.getSkey() }

    fun getPskey(domain: String): String =
        guard("getPskey", "") { TicketManager.getPskey(domain) }

    fun getPt4Token(domain: String): String =
        guard("getPt4Token", "") { TicketManager.getPt4Token(domain) }

    fun getStweb(): String = guard("getStweb", "") { TicketManager.getStweb() }

    /** 由 skey 派生的 bkn；失败返回 0。 */
    fun getBkn(): Long = guard("getBkn", 0L) {
        CalculationUtils.getBkn(TicketManager.getSkey()).toLong() and 0xFFFFFFFFL
    }

    /** 由 skey 派生的 GTK；失败返回空串。 */
    fun getGTK(): String = guard("getGTK", "") {
        CalculationUtils.getBkn(TicketManager.getSkey()).toString()
    }

    // ── 动态加载 ─────────────────────────────────────────────────────────

    /** 加载外部 dex/jar，脚本随后可直接 `import` 其中的类。 */
    fun loadJar(path: String) {
        guard("loadJar", Unit) { runtime.loader.addClassLoader(ScriptJarLoader.load(path)) }
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────

    private inline fun <T> guard(name: String, fallback: T, block: () -> T): T =
        runCatching { block() }
            .onFailure { Log.e("脚本 API $name 失败 [${runtime.info.id}]", it) }
            .getOrDefault(fallback)

    private fun unsupported(name: String) {
        Log.w("脚本 API $name 未适配宿主接口 [${runtime.info.id}]")
    }

    private fun timestamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())

    companion object {

        /** 好友会话。 */
        const val CHAT_FRIEND = MsgConstant.KCHATTYPEC2C

        /** 群会话。 */
        const val CHAT_GROUP = MsgConstant.KCHATTYPEGROUP

        /** 陌生人会话。 */
        const val CHAT_STRANGER = 100
    }
}
