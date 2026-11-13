package com.owo233.tcqt.host.service

internal object FarmAccountProtocol {

    const val APP_ID = "1112386029"
    const val GET_CODE_CMD = "LightAppSvc.mini_program_auth.GetCode"
    const val FAST_LOGIN_CMD = "OidbSvcTrpcTcp.0x94b9_33"
    const val EXTRA_ACCOUNT = "tcqt.farm.account"
    const val EXTRA_NICKNAME = "tcqt.farm.nickname"
    const val EXTRA_SERVICE = "tcqt.farm.login_service"
    const val EXTRA_REPLY = "tcqt.farm.reply"
    const val EXTRA_REQUEST = "tcqt.farm.request"
    const val REQUEST_LOGIN = 1
    const val REQUEST_FAST_LOGIN = 2
    const val SUCCESS = 0
    const val FAILURE = -1

    fun isAccount(uin: String?): Boolean =
        !uin.isNullOrEmpty() && uin.all { it in '0'..'9' } && (uin.toLongOrNull() ?: 0) > 0

    fun mayRoute(command: String): Boolean = command == GET_CODE_CMD || command == FAST_LOGIN_CMD
}

internal data class FarmAccount(val uin: String, val nickname: String, val isCurrent: Boolean)

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
