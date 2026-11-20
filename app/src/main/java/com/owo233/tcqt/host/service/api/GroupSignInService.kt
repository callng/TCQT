package com.owo233.tcqt.host.service.api

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.ProtoNumber
import com.owo233.tcqt.core.proto.ProtoUtils
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.ContactHelper
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qroute.QRouteApi
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import com.tencent.qqnt.msg.api.IMsgService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 解析群号配置，兼容历史写法：
 * - 分隔符可以是半角/全角逗号、顿号、分号、空格（含全角空格）、制表符、换行；
 * - 允许“群号#备注”或“群名 群号”这类混排；
 * - 只保留 5~12 位纯数字的群号。
 *
 * 实现采用“提取数字串”而不是按固定分隔符切分：旧版配置的分隔符只要不是数字，
 * 无论用户用哪种符号都不会丢失，也不会因为新增分隔符而漏读历史值。
 */
internal fun parseGroupNumbers(raw: String): List<String> = raw
    .split(Regex("[^0-9]+"))
    .filter { it.length in 5..12 }
    .distinct()

/**
 * 群打卡的底层能力：原生群签到（OIDB 0xeb7）与群消息打卡。
 *
 * 协议字段取自 XAutoDaily 的 `oidb_0xeb7`：
 * - `OIDBSSOPkg`：1=command、2=serviceType、3=result、4=body、6=clientVersion
 * - `ReqBody`：2=signInWriteReq
 * - `StSignInWriteReq`：1=uid（这里填当前 QQ 号）、2=groupId、3=clientVersion
 */
internal object GroupSignInService {

    private const val TAG = "GroupCheckIn"
    private const val SERVICE_CMD = "OidbSvc.0xeb7"
    private const val OIDB_CMD = 0xEB7
    private const val REQUEST_TIMEOUT_MS = 15_000L
    private const val RETRY_DELAY_MS = 1_500L

    private val GROUP_UIN_FIELDS = listOf("troopuin", "troopUin", "groupUin", "groupCode")
    private val GROUP_NAME_FIELDS = listOf("troopname", "troopName", "groupName", "name")
    private val GROUP_AVATAR_FIELDS = listOf("troopFaceUrl", "faceUrl", "avatarUrl", "headUrl")
    private val GROUP_TIME_FIELDS = listOf("lastMsgTime", "lastActiveTime", "lastMsgTimestamp")
    private val GROUP_LIST_METHODS = listOf(
        "getSortedJoinedTroopInfoFromCache",
        "sortedJoinedTroopInfoFromCache",
        "getJoinedTroopList",
        "getTroopList",
    )

    /** 宿主群列表里的一行，供设置页的群勾选器展示。 */
    data class GroupInfo(
        val uin: String,
        val name: String,
        val avatarUrl: String,
        val lastMsgTime: Long = 0L,
    )

    /**
     * 执行一次原生群签到。
     *
     * 优先走 XA 使用的 OIDB 0xeb7 协议；协议层不可用时退回 QQ 自带的
     * `TroopClockInHandler`，两者都失败才返回 false。
     */
    suspend fun signIn(groupUin: String): Boolean {
        val uin = QQInterfaces.currentUin
        if (uin.isBlank()) {
            Log.e("当前 QQ 号为空，无法签到")
            return false
        }

        val request = runCatching { signInByOidb(groupUin, uin) }
        if (request.isSuccess) return request.getOrDefault(false)

        Log.i("协议直发不可用，尝试宿主原生接口：群 $groupUin")
        Log.e("群 $groupUin 协议签到异常", request.exceptionOrNull())
        return signInByHandler(groupUin, uin)
    }

    /** 向群发送一条打卡文本。 */
    suspend fun sendGroupMessage(groupUin: String, content: String): Boolean {
        val text = content.trim().ifEmpty { "打卡" }
        return runCatching {
            val contact = ContactHelper.generateContact(MsgConstant.KCHATTYPEGROUP, groupUin)
            if (contact !is MapleContact.PublicContact) {
                Log.e("群 $groupUin 的会话对象不可用")
                return@runCatching false
            }
            val element = MsgElement().apply {
                elementType = MsgConstant.KELEMTYPETEXT
                setTextElement(TextElement().apply { this.content = text })
            }
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    QRoute.api(IMsgService::class.java).sendMsg(
                        contact.inner,
                        arrayListOf(element)
                    ) { result, errMsg ->
                        if (result != 0) {
                            Log.e("群 $groupUin 消息发送失败：$result $errMsg")
                        }
                        if (cont.isActive) cont.resume(result == 0)
                    }
                }
            } ?: false
        }.getOrElse {
            Log.e("群 $groupUin 消息发送异常", it)
            false
        }
    }

    /**
     * 读取当前账号已加入的群号。
     *
     * 宿主接口随版本变化，这里按已知方法名逐个尝试，取不到时返回空列表，
     * 由调用方回退到手动填写的群号。
     */
    fun joinedGroupNumbers(): List<String> = joinedGroups().map { it.uin }

    /**
     * 读取当前账号已加入的群，附带群名与头像地址。
     *
     * 群字段名随宿主版本变化，按候选名逐个探测；头像缺失时回退到 QQ 群头像
     * 的标准地址。能读到最近消息时间时按时间倒序，否则保持宿主顺序。
     */
    fun joinedGroups(): List<GroupInfo> = runCatching {
        val groups = readJoinedGroupInfos().mapNotNull { info ->
            val uin = readGroupUin(info) ?: return@mapNotNull null
            if (!uin.matches(Regex("^\\d{5,12}$"))) return@mapNotNull null

            GroupInfo(
                uin = uin,
                name = readText(info, GROUP_NAME_FIELDS)?.trim().orEmpty().ifEmpty { uin },
                avatarUrl = readText(info, GROUP_AVATAR_FIELDS)?.trim()
                    ?.takeIf { it.startsWith("http") }
                    ?: "https://p.qlogo.cn/gh/$uin/$uin/100",
                lastMsgTime = readLong(info, GROUP_TIME_FIELDS),
            )
        }.distinctBy { it.uin }

        if (groups.all { it.lastMsgTime > 0L }) groups.sortedByDescending { it.lastMsgTime } else groups
    }.getOrElse {
        Log.e("读取群列表失败", it)
        emptyList()
    }

    private fun readJoinedGroupInfos(): List<Any> = runCatching {
        val apiClass = load("com.tencent.qqnt.troop.ITroopListRepoApi")
            ?: return@runCatching emptyList()

        @Suppress("UNCHECKED_CAST")
        val api = QRoute.api(apiClass as Class<QRouteApi>)
        val method = api.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && it.name in GROUP_LIST_METHODS
        } ?: return@runCatching emptyList()

        (method.invoke(api) as? Iterable<*>)?.filterNotNull() ?: emptyList()
    }.getOrElse {
        Log.e("读取群列表失败", it)
        emptyList()
    }

    private suspend fun signInByOidb(groupUin: String, uin: String): Boolean {
        val writeReq = ProtoMap().apply {
            this[1] = uin
            this[2] = groupUin
            this[3] = HookEnv.versionName
        }
        val reqBody = ProtoMap().apply {
            this[2] = writeReq
        }
        val oidbPkg = ProtoMap().apply {
            this[1] = OIDB_CMD
            this[2] = 1
            this[3] = 0
            this[4] = reqBody.toByteArray()
            this[6] = "android ${HookEnv.versionName}"
        }

        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine<ByteArray> { cont ->
                PacketHelper.sendRequest(
                    serviceCmd = SERVICE_CMD,
                    rawData = oidbPkg.toByteArray(),
                    receiver = { data ->
                        if (cont.isActive) cont.resume(data)
                    },
                    configure = {
                        extraData.putBoolean("req_pb_protocol_flag", true)
                        addAttribute("req_pb_protocol_flag", true)
                        timeout = REQUEST_TIMEOUT_MS
                    },
                )
            }
        } ?: return false

        val decoded = ProtoUtils.decodeFromByteArray(response)
        val result = (decoded.getOrNull(3) as? ProtoNumber)?.toInt() ?: -1
        if (result != 0) {
            Log.e("群 $groupUin 签到失败，OIDB result=$result")
            return false
        }
        return true
    }

    private fun signInByHandler(groupUin: String, uin: String): Boolean = runCatching {
        val clazz = load("com.tencent.mobileqq.troop.clockin.handler.TroopClockInHandler")
            ?: return@runCatching false
        val handler = QQInterfaces.appRuntime.getBusinessHandler(clazz.name)
            ?: return@runCatching false
        val method = clazz.methods.firstOrNull {
            it.parameterCount == 2 &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.all { type -> type == String::class.java }
        } ?: return@runCatching false

        method.isAccessible = true
        method.invoke(handler, groupUin, uin)
        true
    }.getOrElse {
        Log.e("群 $groupUin 原生接口签到失败", it)
        false
    }

    private fun readGroupUin(info: Any?): String? {
        if (info == null) return null
        return readText(info, GROUP_UIN_FIELDS)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 按候选名依次尝试读取对象的属性：先找同名无参方法，再找 Java getter，
     * 最后找同名字段（宿主类字段多为私有，需要放开访问检查）。
     */
    private fun readText(info: Any, names: List<String>): String? {
        val clazz = info.javaClass
        names.forEach { name ->
            val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
            listOf(name, getter).forEach { methodName ->
                runCatching { clazz.getMethod(methodName).invoke(info) }
                    .getOrNull()?.let { return it.toString() }
            }
            findField(clazz, name)?.let { field ->
                runCatching { field.get(info) }.getOrNull()?.let { return it.toString() }
            }
        }
        return null
    }

    private fun readLong(info: Any, names: List<String>): Long =
        names.firstNotNullOfOrNull { name ->
            val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
            listOf(name, getter).firstNotNullOfOrNull { methodName ->
                runCatching { info.javaClass.getMethod(methodName).invoke(info) }.getOrNull()
            } ?: findField(info.javaClass, name)?.let { field ->
                runCatching { field.get(info) }.getOrNull()
            }
        }.let { value ->
            when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                else -> 0L
            }
        }

    /** 在类及其父类里按名字找字段，忽略大小写与 `m` 前缀（宿主字段命名不统一）。 */
    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val hit = current.declaredFields.firstOrNull { field ->
                val fieldName = field.name
                fieldName.equals(name, ignoreCase = true) ||
                    fieldName.removePrefix("m").removePrefix("_").equals(name, ignoreCase = true)
            }
            if (hit != null) return hit.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }
}
