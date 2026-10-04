package com.owo233.tcqt.core.script

import android.content.ContentResolver
import android.net.Uri

/**
 * 脚本目录里的单个脚本（`ui` 与 `features` 之间传递用的只读模型）。
 *
 * 放在 `core` 而不是 `features.script`：设置界面只允许依赖 `core` / `host`，
 * 脚本引擎的实现细节不能反向漏进 `ui`。
 */
data class ScriptMeta(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val desc: String,
    val dirPath: String,
    val running: Boolean,
    val autoLoad: Boolean,
)

/**
 * 脚本引擎的对外门面。
 *
 * `features.script` 在安装时注入实现，`ui` 与宿主入口只通过它操作脚本，
 * 因此界面层不需要（也不允许）依赖脚本引擎的实现类。
 */
interface ScriptGateway {

    /** 脚本目录绝对路径。 */
    val scriptDir: String

    /** 扫描脚本目录并返回当前脚本列表。 */
    fun list(): List<ScriptMeta>

    fun start(id: String): Boolean

    fun stop(id: String)

    fun reload(id: String): Boolean

    fun delete(id: String)

    fun setAutoLoad(id: String, enabled: Boolean)

    /** 新建脚本骨架；id 或目录重复时返回 false。 */
    fun create(id: String, name: String, version: String, author: String): Boolean

    /** 从 `.zip` 脚本包导入；成功返回 null，失败返回原因。 */
    fun importZip(resolver: ContentResolver, uri: Uri): String?

    companion object {

        /** 由脚本引擎安装时写入；未注入时界面展示空状态。 */
        @Volatile
        var instance: ScriptGateway? = null
    }
}
