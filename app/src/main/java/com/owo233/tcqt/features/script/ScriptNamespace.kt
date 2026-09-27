package com.owo233.tcqt.features.script

import android.app.Activity
import com.owo233.tcqt.core.env.CalculationUtils
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.log.LogUtils
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
import com.tencent.qqnt.troop.ITroopListRepoApi
import com.tencent.mobileqq.data.troop.TroopInfo
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
        ScriptMessenger.sendPic(chatType, peerUin, path)

    fun sendPic(contact: Contact, path: String) =
        ScriptMessenger.sendPic(contact, path)

    fun sendPtt(peerUin: String, path: String, chatType: Int) =
        sendPtt(peerUin, path, chatType, 0)

    fun sendPtt(contact: Contact, path: String) =
        sendPtt(contact, path, 0)

    /** [durationMs] 毫秒；不大于 0 时按 SILK 头估算，估不出用 1 秒。 */
    fun sendPtt(peerUin: String, path: String, chatType: Int, durationMs: Int) =
        ScriptMessenger.sendPtt(chatType, peerUin, path, durationMs)

    fun sendPtt(contact: Contact, path: String, durationMs: Int) =
        ScriptMessenger.sendPtt(contact, path, durationMs)

    fun sendFile(peerUin: String, path: String, chatType: Int) =
        ScriptMessenger.sendFile(chatType, peerUin, path)

    fun sendFile(contact: Contact, path: String) =
        ScriptMessenger.sendFile(contact, path)

    fun sendVideo(peerUin: String, path: String, chatType: Int) =
        ScriptMessenger.sendVideo(chatType, peerUin, path)

    fun sendVideo(contact: Contact, path: String) =
        ScriptMessenger.sendVideo(contact, path)

    fun sendCard(peerUin: String, data: String, chatType: Int) =
        ScriptMessenger.sendCard(chatType, peerUin, data)

    fun sendCard(contact: Contact, data: String) =
        ScriptMessenger.sendCard(contact, data)

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

    /**
     * 全部好友。
     *
     * 宿主 `IFriendsInfoService.getAllFriend("")` 返回的 `bean.d` 上
     * `uin` / `uid` / `nick` / `remark` 都是**未混淆**字段，直接读 getter 即可，
     * 不需要解析 `toString()`。
     *
     * `d.uin` 类型是 `String`，宿主的 `d.uid` 与之对不上时用服务再映射一次。
     */
    fun getAllFriend(): List<FriendInfo> {
        val service = runCatching { QQInterfaces.api<IFriendsInfoService>() }.getOrNull()
            ?: run {
                LogUtils.androidNoFilter.w("脚本 getAllFriend: 取 IFriendsInfoService 失败")
                return emptyList()
            }

        return guard("getAllFriend", emptyList()) {
            service.getAllFriend("").orEmpty().mapNotNull { bean ->
                val uid = runCatching { bean.uid }.getOrNull().orEmpty()
                val uin = runCatching { bean.uin }.getOrNull().orEmpty()
                    .ifEmpty { runCatching { service.getUinFromUid(uid) }.getOrNull().orEmpty() }

                // 两个都取不到就没法标识这个好友，直接跳过
                if (uin.isEmpty() && uid.isEmpty()) return@mapNotNull null

                FriendInfo(
                    uin = uin,
                    uid = uid,
                    name = runCatching { bean.nick }.getOrNull().orEmpty(),
                    remark = runCatching { bean.remark }.getOrNull().orEmpty(),
                )
            }
        }
    }

    /**
     * 名片点赞。
     *
     * @param count 点赞次数
     */
    fun sendZan(uin: String, count: Int) {
        ScriptZan.send(uin, count, isFriend = isFriend(uin))
    }

    // ── 群 ───────────────────────────────────────────────────────────────

    /**
     * 已加入的群列表。
     *
     * 读宿主缓存（`getJoinedTroopInfoFromCache`），不发网络请求。
     * 群号取 `troopuin` 而非 `troopcode`：后者是"可搜索群号"，部分群两者不同。
     */
    fun getGroupList(): List<GroupInfo> {
        val repo = runCatching { QQInterfaces.api<ITroopListRepoApi>() }.getOrNull()
            ?: run {
                LogUtils.androidNoFilter.w("脚本 getGroupList: 取 ITroopListRepoApi 失败")
                return emptyList()
            }

        return guard("getGroupList", emptyList()) {
            val troops = repo.joinedTroops()
            troops.map { troop ->
                GroupInfo(
                    group = troop.troopuin.orEmpty(),
                    groupName = troop.troopname.orEmpty()
                        .ifEmpty { troop.troopNameFromNT.orEmpty() },
                    groupOwner = troop.troopowneruin.orEmpty(),
                    groupInfo = troop,
                )
            }.filter { it.group.isNotEmpty() }
        }
    }

    /** 已加入的群；优先「已加入」，为空再退「已排序」。 */
    private fun ITroopListRepoApi.joinedTroops(): List<TroopInfo> =
        runCatching { getJoinedTroopInfoFromCache() }.getOrNull().orEmpty()
            .ifEmpty { runCatching { getSortedJoinedTroopInfoFromCache() }.getOrNull().orEmpty() }

    fun getGroupInfo(groupUin: String): Any? =
        guard("getGroupInfo", null) { GroupService.getGroupInfo(groupUin) }

    /**
     * 群成员列表。
     *
     * 走内核 `IKernelGroupService.getAllMemberList(groupId, false, callback)`，回调是异步的，
     * 这里用带超时的阻塞等待把它变成同步返回值 —— 脚本回调都能接受一次性等待，
     * 但**不会**无限等（超时返回空列表），避免把宿主的推送线程挂死。
     *
     * 群服务经 `IKernelService.getGroupService()` 再取一层 `service` 拿到；
     * 中间层类型在宿主里是 Kotlin 内部类，不便进桩，因此用反射取。
     */
    fun getGroupMemberList(groupUin: String): List<MemberInfo> {
        val service = kernelGroupService() ?: return emptyList()
        val groupId = groupUin.toLongOrNull() ?: return emptyList()

        val latch = java.util.concurrent.CountDownLatch(1)
        var result: List<MemberInfo> = emptyList()

        return guard("getGroupMemberList", emptyList()) {
            service.getAllMemberList(
                groupId,
                false,
            ) { code, _, data ->
                if (code == 0) {
                    result = data?.infos?.values.orEmpty().map { member ->
                        MemberInfo(
                            uin = member.uin.toString(),
                            uinName = member.cardName.ifEmpty { member.nick },
                            uinLevel = member.memberRealLevel,
                            joinGroupTime = member.joinTime.toLong(),
                            lastActiveTime = member.lastSpeakTime.toLong(),
                            role = member.role?.name ?: "MEMBER",
                            shutUpEndTime = member.shutUpTime.toLong(),
                            memberInfo = member,
                        )
                    }
                }
                latch.countDown()
            }

            if (!latch.await(MEMBER_LIST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                LogUtils.androidNoFilter.w("脚本 getGroupMemberList: 等待超时 group=$groupUin")
            }
            result
        }
    }

    /** 取内核群服务；任一层取不到都返回 null（并记一次诊断）。 */
    private fun kernelGroupService(): com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService? =
        runCatching {
            val kernel = QQInterfaces.runtime<com.tencent.qqnt.kernel.api.IKernelService>()

            // kernelService.getGroupService().getService()
            val wrapper = kernel.javaClass.getMethod("getGroupService").invoke(kernel)
            val inner = wrapper?.javaClass?.getMethod("getService")?.invoke(wrapper)
            inner as? com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService
        }.onFailure {
            LogUtils.androidNoFilter.w("脚本 getGroupMemberList: 取内核群服务失败", it)
        }.getOrNull()

    fun getMemberInfo(groupUin: String, uin: String): MemberInfo? =
        getGroupMemberList(groupUin).firstOrNull { it.uin == uin }

    /**
     * 群禁言列表。
     *
     * 宿主没有单独的"禁言列表"接口：禁言状态挂在成员信息上
     * （`MemberInfo.shutUpTime` 是**禁言结束时间戳**，不是时长），因此从成员列表里筛。
     * `shutUpTime` 为过去时间或 0 表示未禁言。
     */
    fun getProhibitList(groupUin: String): List<ForbidInfo> {
        val now = System.currentTimeMillis() / 1000

        return guard("getProhibitList", emptyList()) {
            getGroupMemberList(groupUin).mapNotNull { member ->
                val endTime = member.shutUpEndTime
                val remaining = endTime - now
                if (remaining <= 0) return@mapNotNull null

                ForbidInfo(
                    user = member.uin,
                    userName = member.uinName,
                    time = remaining,
                    endTime = endTime,
                )
            }
        }
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

    /** 群打卡。 */
    fun clockIn(groupUin: String) {
        ScriptClockIn.clockIn(groupUin)
    }

    // ── 票据 ─────────────────────────────────────────────────────────────

    /** skey。宿主未暴露"real skey"，与 [getSkey] 同值。 */
    fun getRealSkey(): String = getSkey()

    fun getSkey(): String = guard("getSkey", "") { TicketManager.getSkey() }

    fun getStweb(): String = guard("getStweb", "") { TicketManager.getStweb() }

    fun getPskey(url: String): String = guard("getPskey", "") { TicketManager.getPskey(url) }

    fun getPt4Token(url: String): String =
        guard("getPt4Token", "") { TicketManager.getPt4Token(url) }

    /**
     * 由任意 key 派生 bkn（djb2 变体，与宿主/模块内实现一致）。
     *
     * 文档签名是 `getBkn(String key)`：传 skey 得登录态 bkn，传 pskey 得该域名的 bkn。
     */
    fun getBkn(key: String): Long = guard("getBkn", 0L) {
        CalculationUtils.getBkn(key).toLong() and 0xFFFFFFFFL
    }

    /**
     * 由 URL 的 pskey 派生 GTK。
     *
     * 文档签名是 `getGTK(String url)`；拿不到 pskey 时返回空串。
     */
    fun getGTK(url: String): String = guard("getGTK", "") {
        val pskey = TicketManager.getPskey(url)
        if (pskey.isEmpty()) "" else (CalculationUtils.getBkn(pskey).toLong() and 0xFFFFFFFFL).toString()
    }

    fun getGroupRKey(): String = guard("getGroupRKey", "") { ScriptRKey.group() }

    fun getFriendRKey(): String = guard("getFriendRKey", "") { ScriptRKey.friend() }

    // ── 动态加载 ─────────────────────────────────────────────────────────

    /** 加载已 dex 化的 jar/apk，脚本随后可直接 `import` 其中的类。 */
    fun loadJar(path: String) {
        guard("loadJar", Unit) { runtime.loader.addClassLoader(ScriptLoader.loadJar(path)) }
    }

    /** 加载 dex 文件。 */
    fun loadDex(path: String) {
        guard("loadDex", Unit) { runtime.loader.addClassLoader(ScriptLoader.loadDex(path)) }
    }

    /**
     * 加载并执行一段 Java 源码。
     *
     * 与 `loadJava(路径)` 等价：旧版 BeanShell 靠运行期 Java 编译器实现，现在统一
     * 走 BeanShell 自己的解释器（`Interpreter.source`），不再依赖 javac。
     */
    fun loadJava(path: String) {
        guard("loadJava", Unit) {
            val file = java.io.File(runtime.info.dirPath, path)
            val target = if (file.exists()) file else java.io.File(path)
            runtime.interpreter.source(target.absolutePath)
        }
    }

    /** 注册脚本自己的 Activity，之后可用 `startActivity` 启动。 */
    fun registerActivity(activityClass: Class<*>) {
        guard("registerActivity", Unit) { runtime.registerActivity(activityClass) }
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

        /** 群成员列表等待上限（秒）：宿主回调是异步的，超时返回已拿到的部分。 */
        private const val MEMBER_LIST_TIMEOUT_SECONDS = 5L

        /** 好友会话。 */
        const val CHAT_FRIEND = MsgConstant.KCHATTYPEC2C

        /** 群会话。 */
        const val CHAT_GROUP = MsgConstant.KCHATTYPEGROUP

        /** 陌生人会话。 */
        const val CHAT_STRANGER = 100
    }
}
