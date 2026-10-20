package com.owo233.tcqt.host.service.api

import com.owo233.tcqt.core.proto.ProtoMap
import com.owo233.tcqt.core.proto.ProtoUtils
import com.owo233.tcqt.core.proto.asInt
import com.owo233.tcqt.core.proto.asUtf8String

internal enum class AdminPermission(val id: Int, val title: String, val description: String? = null) {
    RECEIVE_VERIFY(0, "接收验证消息"),
    RECALL(2, "撤回成员消息"),
    KICK(1, "移除群成员"),
    BASIC(10000, "基础管理", "群资料管理 · 群分类 · 个性管理"),
    JOIN(20000, "加群管理", "加群方式 · 加群自动审批 · 群搜索方式"),
    MEMBER(30000, "成员管理", "成员数据 · 群黑名单管理"),
    SPEAK(40000, "发言管理", "群内禁言 · 禁发特定消息 · 发言限频"),
    SMART(50000, "智能消息", "入群欢迎 · 定时消息 · 自由问答");
}

internal data class AdminPermissionSnapshot(
    val enabled: Map<AdminPermission, Boolean>,
    val maxKickPerDay: Int
)

internal class AdminPermissionException(message: String) : IllegalStateException(message)

/** QQ 9.3.75: kg0.a / jg0.a,b,c; receive verification uses a separate kernel API. */
internal object AdminPermissionProtocol {
    const val COMMAND = "OidbSvcTrpcTcp.0x9c1c_2"

    val kickLimits = listOf(0, 5, 10, 20, 50, 100, 200)

    fun validateLimit(permission: AdminPermission, enabled: Boolean, limit: Int) {
        require(limit >= 0)
        require(permission != AdminPermission.KICK || !enabled || limit in kickLimits) {
            "每日可移除人数仅支持不限制、5、10、20、50、100、200"
        }
    }

    fun encodeChange(
        group: Long, target: Long, permission: AdminPermission, enabled: Boolean, limit: Int
    ): ByteArray {
        require(group > 0 && target > 0)
        require(permission != AdminPermission.RECEIVE_VERIFY)
        validateLimit(permission, enabled, limit)
        return ProtoMap().apply {
            this[1] = 0x9c1c
            this[2] = 2
            this[3] = 0
            this[4, 1] = group
            this[4, 2] = target
            this[4, 3, 1] = permission.id
            this[4, 3, 2] = if (enabled) 1 else 2
            this[4, 3, 3] = if (permission == AdminPermission.KICK && enabled) {
                ProtoMap().apply { this[1] = limit }.toByteArray()
            } else byteArrayOf()
        }.toByteArray()
    }

    fun checkResponse(bytes: ByteArray) {
        if (bytes.isEmpty()) throw AdminPermissionException("请求失败，未收到服务器响应")
        val response = ProtoUtils.decodeFromByteArray(bytes)
        if (response.getOrNull(1)?.asInt != 0x9c1c || response.getOrNull(2)?.asInt != 2) {
            throw AdminPermissionException("管理员权限响应不匹配")
        }
        // An omitted protobuf scalar has its schema default (0).
        val code = response.getOrNull(3)?.asInt ?: 0
        if (code != 0) {
            val message = response.getOrNull(5)?.asUtf8String.orEmpty().ifBlank { "修改失败" }
            throw AdminPermissionException("$message ($code)")
        }
    }

    fun decodeKickLimit(extra: ByteArray): Int {
        val limit = if (extra.isEmpty()) 0 else
            ProtoUtils.decodeFromByteArray(extra).getOrNull(1)?.asInt ?: 0
        require(limit >= 0) { "服务器返回了当前客户端不支持的人数范围" }
        return limit
    }

    fun isEnabled(value: Int?): Boolean = when (value) {
        null, 0, 1 -> true // QQ's ordinary administrator defaultOn is true.
        2 -> false
        else -> throw AdminPermissionException("未知管理员权限状态：$value")
    }
}
