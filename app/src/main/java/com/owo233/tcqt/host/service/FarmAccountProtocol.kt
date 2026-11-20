package com.owo233.tcqt.host.service

internal object FarmAccountProtocol {

    const val APP_ID = "1112386029"
    const val GET_CODE_CMD = "LightAppSvc.mini_program_auth.GetCode"
    const val FAST_LOGIN_CMD = "OidbSvcTrpcTcp.0x94b9_33"
    const val HTTP_TRANSFER_CMD = "miniapp.trpc.minigame.transfer.Transfer.HTTPTransfer"
    const val EXTRA_ACCOUNT = "tcqt.farm.account"
    const val EXTRA_NICKNAME = "tcqt.farm.nickname"
    const val EXTRA_SERVICE = "tcqt.farm.login_service"
    const val EXTRA_CUSTOM_LOGIN = "tcqt.farm.custom_login"
    const val EXTRA_SESSION_ID = "tcqt.farm.session_id"
    const val EXTRA_REPLY = "tcqt.farm.reply"
    const val EXTRA_REQUEST = "tcqt.farm.request"
    const val REQUEST_LOGIN = 1
    const val REQUEST_FAST_LOGIN = 2
    const val SUCCESS = 0
    const val FAILURE = -1

    fun isAccount(uin: String?): Boolean =
        !uin.isNullOrEmpty() && uin.all { it in '0'..'9' } && (uin.toLongOrNull() ?: 0) > 0

    fun commandFor(operation: Int): String? = when (operation) {
        REQUEST_LOGIN -> GET_CODE_CMD
        REQUEST_FAST_LOGIN -> FAST_LOGIN_CMD
        else -> FarmFriendOperation.fromId(operation)?.let { HTTP_TRANSFER_CMD }
    }

    fun mayRoute(command: String): Boolean = command == GET_CODE_CMD || command == FAST_LOGIN_CMD ||
        command == HTTP_TRANSFER_CMD

    fun matchesRequest(operation: Int, command: String): Boolean = commandFor(operation) == command

    fun isFarmPreloadKey(key: String): Boolean {
        val prefix = "key_preload_login_info"
        return !(!key.startsWith(prefix) || !key.endsWith(APP_ID)) &&
                isAccount(key.removePrefix(prefix).removeSuffix(APP_ID))
    }

    fun normalizeCustomCode(input: String): String {
        val code = input.trim()
        require(code.isNotEmpty()) { "请先粘贴 js_code" }
        require(code.length <= 4096) { "js_code 过长，请只粘贴登录码本身" }
        require(code.none { it.isWhitespace() || it.isISOControl() }) {
            "请粘贴完整的 js_code，内容不能包含空格或换行"
        }
        return code
    }
}

/** QQ farm uses HTTPTransfer, not the WeChat branch's getFriendCloudStorage API. */
internal enum class FarmFriendOperation(val id: Int, val path: String) {
    PLAYING(3, "get_friend_storage"),
    RECENT(4, "get_recent_friends"),
    RECOMMENDED(5, "get_recommend_friends"),
    CHECK(6, "check_friend");

    val url: String = "http://fakeapi.qq.com/$path"

    companion object {
        fun fromId(id: Int): FarmFriendOperation? = entries.firstOrNull { it.id == id }
        fun fromRequest(appId: String, method: String, url: String): FarmFriendOperation? =
            if (appId == FarmAccountProtocol.APP_ID && method == "POST") entries.firstOrNull { it.url == url }
            else null
    }
}

internal data class FarmAccount(val uin: String, val nickname: String, val isCurrent: Boolean)

/** Imported fast-login credentials have no QQ account capability and cannot be refreshed. */
internal class FarmCustomCode(input: String) {
    private var value: String? = FarmAccountProtocol.normalizeCustomCode(input)

    @Synchronized
    fun take(operation: Int): String? {
        if (operation != FarmAccountProtocol.REQUEST_FAST_LOGIN) return null
        return value.also { value = null }
    }
}

/** A preflight code is usable once, briefly, and only for its original account and API. */
internal class FarmPreflightCode(
    private val account: String,
    code: String,
    private val createdAt: Long,
) {
    private var value: String? = code

    @Synchronized
    fun take(uin: String, operation: Int, now: Long): String? {
        if (uin != account || operation != FarmAccountProtocol.REQUEST_FAST_LOGIN) return null
        val result = value
        value = null
        return result?.takeIf { now - createdAt in 0..30_000 }
    }
}
