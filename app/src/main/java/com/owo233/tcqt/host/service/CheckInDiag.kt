package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 群打卡自证日志。
 *
 * **主写入位置**是模块数据目录，它落在宿主自己的私有 files 下
 * （Zygisk 与 LSPosed 两种形态都由 `HookSteps.initContext` 统一设置 `HookEnv.moduleDataPath`，
 * 路径完全一致，与注入方式无关）：
 *
 * ```
 * /data/user/<用户id>/<宿主包名>/files/<模块目录>/group_check_in_diag.log
 * ```
 *
 * 同时**尽力镜像**一份到宿主的应用专属外部目录，用户用文件管理器即可取出，无需 root：
 *
 * ```
 * /sdcard/Android/data/<宿主包名>/files/<模块名>/group_check_in_diag.log
 * ```
 *
 * 注意：`/data/adb/tcqt` 是 Zygisk 模块的**安装目录**（root 所有），只放 `main.apk` 等载荷，
 * 模块代码以宿主 uid 运行，写不进去也不该往那里写——日志从来不在该目录。
 */
internal object CheckInDiag {

    private const val FILE_NAME = "group_check_in_diag.log"
    private const val MAX_BYTES = 256 * 1024

    /** 本进程最近一次成功写入的位置（主位置）。 */
    @Volatile
    var lastPath: String = ""
        private set

    /** 最近一次写入失败的可见原因；成功写入时清空。 */
    @Volatile
    var lastError: String = ""
        private set

    /** 外部镜像位置；拿不到外部目录或镜像失败时为空。 */
    @Volatile
    var mirrorPath: String = ""
        private set

    fun log(text: String) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val line = "$stamp $text\n"

        val primary = primaryFile()
        val primaryResult = runCatching { write(primary, line) }
        if (primaryResult.isSuccess) {
            lastPath = primary.absolutePath
            lastError = ""
        } else {
            lastError = primaryResult.exceptionOrNull()?.message ?: "写入异常"
            Log.w("群打卡日志：写入 $primary 失败（$lastError）")
        }

        // 镜像只是方便导出：失败不影响主日志，也不覆盖主日志的错误状态。
        mirrorFile()?.let { mirror ->
            runCatching { write(mirror, line) }
                .onSuccess { mirrorPath = mirror.absolutePath }
                .onFailure { Log.w("群打卡日志：镜像写入 $mirror 失败（${it.message}）") }
        }
    }

    /** 给界面展示的实际路径：主位置 + 镜像（取得到时）。 */
    fun pathText(): String {
        val primary = lastPath.ifBlank { runCatching { primaryFile().absolutePath }.getOrDefault("") }
        return when {
            primary.isBlank() -> "模块数据目录尚未就绪"
            mirrorPath.isNotBlank() -> "$primary\n镜像：$mirrorPath"
            else -> primary
        }
    }

    private fun write(file: File, line: String) {
        file.parentFile?.mkdirs()
        if (file.length() > MAX_BYTES) file.delete()
        file.appendText(line, Charsets.UTF_8)
    }

    private fun primaryFile(): File = File(HookEnv.moduleDataPath, FILE_NAME)

    private fun mirrorFile(): File? = runCatching {
        HookEnv.hostAppContext.getExternalFilesDir(null)
            ?.let { File(it, "${TCQTBuild.APP_NAME}/$FILE_NAME") }
    }.getOrNull()
}
