package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 一个群今天的打卡记录。[at] 为完成时刻（秒）。 */
@Serializable
internal data class GroupCheckInRecord(
    val uin: String,
    val name: String = "",
    val at: Long = 0,
)

/** 今天打卡失败的群，保留到该群重新打卡成功为止。 */
@Serializable
internal data class GroupCheckInFailure(
    val uin: String,
    val name: String = "",
    val at: Long = 0,
    val reason: String = "",
)

/** 按天存放：日期变了整份数据作废（等价于每天 0 点自动清除）。 */
@Serializable
internal data class GroupCheckInDayLog(
    val date: String = "",
    val records: List<GroupCheckInRecord> = emptyList(),
    val failures: List<GroupCheckInFailure> = emptyList(),
    /** 最近一次实际执行的目标群：点击「立即执行一次」时先用它判断今天是否已全部完成。 */
    val targets: List<String> = emptyList(),
    /** 最近一次执行时的范围模式（1=指定群号，2=全部已加入群，0=未知）。 */
    val targetMode: Int = 0,
)

/**
 * 群打卡监控数据。
 *
 * 只保留“今天成功打卡的群”，落在 `<moduleDataPath>/group_check_in_today.json`。
 * 跨天判定用本地日期字符串：读取/写入/定时检查时发现 [GroupCheckInDayLog.date] 不是今天，
 * 就整份清空并重写。因此过了 0 点以后面板自然为空，不依赖任何系统定时任务。
 */
internal object GroupCheckInLog {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val lock = Any()

    private fun file(): File = File(HookEnv.moduleDataPath, "group_check_in_today.json")

    private fun todayDate(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    /** 读取今日记录；跨天自动清空。 */
    fun today(): GroupCheckInDayLog = synchronized(lock) { readUnlocked() }

    /** 记一个群打卡成功；同一天同一群只保留一条（重试成功不会重复），并清掉该群的失败记录。 */
    fun record(uin: String, name: String) {
        if (uin.isBlank()) return
        synchronized(lock) {
            val current = readUnlocked()
            val withoutFailure = current.copy(
                failures = current.failures.filterNot { it.uin == uin },
            )
            if (current.records.any { it.uin == uin }) {
                if (withoutFailure != current) writeUnlocked(withoutFailure)
                return
            }
            val next = withoutFailure.copy(
                records = withoutFailure.records + GroupCheckInRecord(
                    uin = uin,
                    name = name.takeIf { it.isNotBlank() && it != uin } ?: "",
                    at = System.currentTimeMillis() / 1000,
                ),
            )
            writeUnlocked(next)
        }
    }

    /** 记一个群今天打卡失败；同一群只保留最近一次失败原因。 */
    fun recordFailure(uin: String, name: String, reason: String) {
        if (uin.isBlank()) return
        synchronized(lock) {
            val current = readUnlocked()
            val failure = GroupCheckInFailure(
                uin = uin,
                name = name.takeIf { it.isNotBlank() && it != uin } ?: "",
                at = System.currentTimeMillis() / 1000,
                reason = reason.trim(),
            )
            writeUnlocked(
                current.copy(
                    failures = current.failures.filterNot { it.uin == uin } + failure,
                ),
            )
        }
    }

    /** 该群今天已经成功（例如命中 success_state），把残留的失败记录清掉。 */
    fun clearFailure(uin: String) {
        if (uin.isBlank()) return
        synchronized(lock) {
            val current = readUnlocked()
            if (current.failures.none { it.uin == uin }) return
            writeUnlocked(current.copy(failures = current.failures.filterNot { it.uin == uin }))
        }
    }

    /** 定时调用：跨天时把旧数据清掉，保证 0 点后立即为空。 */
    fun ensureToday() {
        synchronized(lock) { readUnlocked() }
    }

    /**
     * 记录本次执行的目标群，供下次点击时快速判断“今天是否已全部完成”。
     *
     * 存在今日记录文件里，和成功记录同生共死：跨天或删除今日记录后一起失效，
     * 不会出现“记录已清掉、目标还在”的错位。
     */
    fun recordTargets(mode: Int, targets: List<String>) {
        val clean = targets.filter { it.isNotBlank() }.distinct()
        synchronized(lock) {
            val current = readUnlocked()
            if (current.targetMode == mode && current.targets == clean) return
            writeUnlocked(current.copy(targetMode = mode, targets = clean))
        }
    }

    /**
     * 清空今日记录（成功与失败一起删）。
     *
     * 用于用户强制重打的场景：清掉后今天已打卡的群会被当作未打卡，再次执行会重新打卡。
     * 只删记录文件，不动群打卡的任何配置。
     */
    fun clearToday(): Boolean = synchronized(lock) {
        val target = file()
        runCatching { File(target.parentFile, "${target.name}.tmp").delete() }
        runCatching { if (target.isFile) target.delete() else true }.getOrDefault(false)
    }

    private fun readUnlocked(): GroupCheckInDayLog {
        val record = runCatching {
            val target = file()
            if (!target.isFile) return@runCatching GroupCheckInDayLog()
            json.decodeFromString(GroupCheckInDayLog.serializer(), target.readText(Charsets.UTF_8))
        }.getOrDefault(GroupCheckInDayLog())

        val day = todayDate()
        if (record.date == day) return record
        // 跨天：删掉旧文件，返回空。
        runCatching { file().delete() }
        return GroupCheckInDayLog(date = day)
    }

    private fun writeUnlocked(value: GroupCheckInDayLog) {
        runCatching {
            val target = file()
            target.parentFile?.mkdirs()
            val text = json.encodeToString(GroupCheckInDayLog.serializer(), value)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("无法写入 ${target.absolutePath}")
            }
        }.onFailure { Log.e("群打卡：保存今日记录失败", it) }
    }
}
