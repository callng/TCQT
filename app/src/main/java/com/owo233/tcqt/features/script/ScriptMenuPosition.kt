package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.proto.GlobalJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * 悬浮球位置持久化。
 *
 * 存成脚本缓存目录下的一个小 JSON 文件，而不是塞进模块主配置：球位置是一次性的 UI
 * 状态，不该占一个需要长期兼容的正式配置 key。
 */
internal object ScriptMenuPosition {

    const val INVALID = -1

    private val file: File
        get() = File(HookEnv.moduleDataPath, "script/.cache/float_ball.json")

    fun load(): Pair<Int, Int> = runCatching {
        if (!file.exists()) return INVALID to INVALID
        val obj = GlobalJson.parseToJsonElement(file.readText()).jsonObject
        val x = (obj["x"] as? JsonPrimitive)?.intOrNull ?: INVALID
        val y = (obj["y"] as? JsonPrimitive)?.intOrNull ?: INVALID
        x to y
    }.onFailure {
        LogUtils.androidNoFilter.w("脚本悬浮菜单: 位置读取失败", it)
    }.getOrDefault(INVALID to INVALID)

    fun save(x: Int, y: Int) {
        runCatching {
            file.parentFile?.mkdirs()
            val obj = JsonObject(mapOf("x" to JsonPrimitive(x), "y" to JsonPrimitive(y)))
            file.writeText(GlobalJson.encodeToString(JsonObject.serializer(), obj))
        }.onFailure {
            LogUtils.androidNoFilter.w("脚本悬浮菜单: 位置写入失败", it)
        }
    }
}