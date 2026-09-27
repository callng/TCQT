package com.owo233.tcqt.features.script.bean

import java.io.File

/**
 * 脚本元数据（对应脚本目录下的 `info.prop` 与 `desc.txt`）。
 *
 * [id] 一经使用不可更改：脚本自己的配置文件目录以它命名。
 */
data class ScriptInfo(
    val id: String,
    var name: String,
    var version: String,
    var author: String,
    val dirPath: String,
) {

    /** 运行期状态，由 `ScriptCore` 维护，不持久化。 */
    @Volatile
    var isRunning: Boolean = false

    var desc: String = ""

    init {
        updateFromDisk()
    }

    /** 宿主直接改脚本文件后，重新读取元数据。 */
    fun updateFromDisk() {
        runCatching {
            val dir = File(dirPath)

            val propFile = File(dir, PROP_FILE)
            if (propFile.exists()) {
                val props = java.util.Properties()
                propFile.reader(Charsets.UTF_8).use { props.load(it) }
                name = props.getProperty("pluginName", name)
                version = props.getProperty("versionCode", version)
                author = props.getProperty("author", author)
            }

            val descFile = File(dir, DESC_FILE)
            desc = if (descFile.exists()) descFile.readText() else ""
        }
    }

    companion object {

        const val ENTRY_FILE = "main.java"
        const val PROP_FILE = "info.prop"
        const val DESC_FILE = "desc.txt"
        const val CONFIG_DIR = "config"

        /** 从目录读取脚本；缺少 `info.prop` 或没有 `id` 时返回 null。 */
        fun fromDir(dir: File): ScriptInfo? = runCatching {
            val propFile = File(dir, PROP_FILE)
            if (!propFile.exists()) return null

            val props = java.util.Properties()
            propFile.reader(Charsets.UTF_8).use { props.load(it) }

            val id = props.getProperty("id")?.takeIf { it.isNotBlank() } ?: return null

            ScriptInfo(
                id = id,
                name = props.getProperty("pluginName", id),
                version = props.getProperty("versionCode", "1.0"),
                author = props.getProperty("author", "Unknown"),
                dirPath = dir.absolutePath,
            )
        }.getOrNull()
    }
}
