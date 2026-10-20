package com.owo233.tcqt.features.advanced

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal object TroopAdminPermissionConfig {
    const val KEY = "group_admin_permission_config"
    private val extendedPermissionIds = setOf(10000, 20000, 30000, 40000, 50000)

    fun forceEnable(raw: String?): String {
        val original = runCatching { Json.parseToJsonElement(raw.orEmpty()) as? JsonObject }.getOrNull()
        val result = original?.toMutableMap() ?: mutableMapOf()
        for (type in listOf("admin", "robot")) {
            val fallback = defaults.getValue(type) as JsonObject
            val node = result[type] as? JsonObject
            val enabled = JsonObject((node ?: fallback).toMutableMap().apply {
                put("enable", JsonPrimitive(true))
                if (!hasPermissionItems(node)) put("groups", fallback.getValue("groups"))
            })
            result[type] = if (type == "admin") unlockExtendedPermissions(enabled) else enabled
        }
        // 原生与 Kuikly 都优先读目标 UIN 节点 无效/禁用的 UIN 节点会挡住
        // admin/robot 回退 移除这些本地副本节点 由宿主按真实机器人身份回退
        // 不能统一补 admin 项 否则会把机器人套入普通管理员的配置
        original?.forEach { (key, value) ->
            if (key.isNotEmpty() && key.all(Char::isDigit)) {
                val node = value as? JsonObject
                if (node == null || node["enable"].asBoolean() != true || !hasPermissionItems(node)) {
                    result.remove(key)
                } else {
                    result[key] = unlockExtendedPermissions(node)
                }
            }
        }
        return JsonObject(result).toString()
    }

    // 不修改 defaultOn 或权限状态 也不把未知 ID 和机器人默认项一律放开
    private fun unlockExtendedPermissions(node: JsonObject): JsonObject {
        val groups = node["groups"] as? JsonArray ?: return node
        return JsonObject(node.toMutableMap().apply {
            put("groups", JsonArray(groups.map { group ->
                if (group !is JsonArray) return@map group
                JsonArray(group.map permission@{ value ->
                    val item = value as? JsonObject ?: return@permission value
                    val id = (item["itemId"] as? JsonPrimitive)?.intOrNull
                    if (id !in extendedPermissionIds) return@permission item
                    JsonObject(item.toMutableMap().apply { put("canUse", JsonPrimitive(true)) })
                })
            }))
        })
    }

    fun describe(raw: String?): String {
        val root = runCatching { Json.parseToJsonElement(raw.orEmpty()) as? JsonObject }.getOrNull()
        if (root == null) return if (raw.isNullOrBlank()) "empty" else "invalid"
        return listOf("admin", "robot").joinToString(",") { type ->
            val node = root[type] as? JsonObject
            "$type(enable=${node?.get("enable").asBoolean()},items=${hasPermissionItems(node)})"
        }
    }

    private fun hasPermissionItems(node: JsonObject?): Boolean =
        (node?.get("groups") as? JsonArray)?.any { group ->
            (group as? JsonArray)?.any { value ->
                val item = value as? JsonObject
                item?.get("isRcvJoinVerify").asBoolean() == true ||
                    ((item?.get("itemId") as? JsonPrimitive)?.intOrNull ?: 0) > 0
            } == true
        } == true

    private fun kotlinx.serialization.json.JsonElement?.asBoolean() =
        (this as? JsonPrimitive)?.booleanOrNull

    private val defaults by lazy { Json.parseToJsonElement(DEFAULT_JSON) as JsonObject }

    // 从 wo0.a$a.invoke(9.3.75) 的字符串常量逐字提取，扩展项的客户端限制在副本中解除
    private const val DEFAULT_JSON = """
{
  "admin": {
    "enable": true,
    "groups": [
      [
        {"title": "接收验证消息", "canUse": true, "isRcvJoinVerify": true},
        {"title": "撤回成员消息", "canUse": true, "itemId": 2, "defaultOn": true},
        {
          "title": "移除群成员", "canUse": true, "itemId": 1, "isKickMember": true, "defaultOn": true,
          "kickLimit": {
            "title": "每日可移除人数",
            "options": [
              {"value": 0, "title": "不限制"},
              {"value": 5, "title": "5人"},
              {"value": 10, "title": "10人"},
              {"value": 20, "title": "20人"},
              {"value": 50, "title": "50人"},
              {"value": 100, "title": "100人"},
              {"value": 200, "title": "200人"}
            ]
          }
        }
      ],
      [
        {"title": "基础管理", "desc": "群资料管理 · 群分类 · 个性管理", "canUse": false, "itemId": 10000, "defaultOn": true},
        {"title": "加群管理", "desc": "加群方式 · 加群自动审批 · 群搜索方式", "canUse": false, "itemId": 20000, "defaultOn": true},
        {"title": "成员管理", "desc": "成员数据 · 群黑名单管理", "canUse": false, "itemId": 30000, "defaultOn": true},
        {"title": "发言管理", "desc": "群内禁言 · 禁发特定消息 · 发言限频", "canUse": false, "itemId": 40000, "defaultOn": true},
        {"title": "智能消息", "desc": "入群欢迎 · 定时消息 · 自助问答", "canUse": false, "itemId": 50000, "icon": "robot", "defaultOn": true}
      ]
    ]
  },
  "robot": {
    "enable": true,
    "subTitle": "设置权限后，如机器人具备该能力，即可在本群直接使用",
    "groups": [
      [
        {"title": "撤回成员消息", "canUse": false, "itemId": 2, "defaultOn": true},
        {"title": "移除群成员", "canUse": false, "itemId": 1, "isKickMember": true, "defaultOn": true}
      ],
      [
        {"title": "加群管理", "desc": "加群方式 · 加群自动审批 · 群搜索方式", "canUse": false, "itemId": 20000, "defaultOn": true},
        {"title": "成员管理", "desc": "成员数据 · 群黑名单管理", "canUse": false, "itemId": 30000, "defaultOn": true},
        {"title": "发言管理", "desc": "群内禁言 · 禁发特定消息 · 发言限频", "canUse": false, "itemId": 40000, "defaultOn": true}
      ]
    ]
  }
}
    """
}
