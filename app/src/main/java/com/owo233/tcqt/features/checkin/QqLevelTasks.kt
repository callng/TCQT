package com.owo233.tcqt.features.checkin

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.IntOption
import com.owo233.tcqt.api.Option
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQToastCompat
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.TicketManager
import com.tencent.qphone.base.util.BaseApplication
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * QQ 等级任务：日签卡 / 收集卡。
 *
 * 参照 XAutoDaily 3.0.37 的配置任务（`ti.qq.com` 的 daily_attendance 接口）：
 * - 日签卡：`SignIn` body `{"uin":..,"type":1,"qua":..}`，随后
 *   `qqlevel/trpc/levelTask/Refresh?bkn=` body `{"task_id":"3","mode":1}` 领取 0.5 活跃天；
 * - 收集卡：先 `SignInMainPage?bkn=` 取 `data.vecSignInfo.value[2].subType`，
 *   再 `SignIn` body `{"uin":..,"type":4,"subType":..,"qua":..}`。
 *
 * 鉴权只用宿主 `TicketManager` 的 pskey（算 bkn）与 cookie，qua 取宿主自己的值，
 * 不依赖任何 Xposed/Zygisk 专有 API，两种注入模式行为一致。
 */
@RegisterAction
object QqLevelTasks : Feature(
    key = "qq_level_tasks",
    name = "QQ日签卡 / 收集卡",
    desc = "完成 QQ 等级任务里的日签卡与收集卡；触发方式支持仅手动、每天定时、启动后自动。",
    uiOrder = 12,
    actionLabel = "立即执行一次",
) {

    private const val TAG = "QqLevelTasks"
    private const val TICK_MS = 30_000L
    private const val FIRST_DELAY_MS = 10_000L

    /*
     * 单选值按界面约定是 1-based：options 第 i 项存 i + 1（见 FeatureCatalog 的 OptionItem）。
     * 所以 ["关闭", "开启"] 里“关闭”=1、“开启”=2，不能用 1 判定开启。
     */
    private const val SWITCH_ON = 2

    private const val TRIGGER_DAILY = 1
    private const val TRIGGER_STARTUP = 2
    private const val TRIGGER_MANUAL = 3

    private const val DOMAIN = "ti.qq.com"
    private const val BASE_URL = "https://ti.qq.com"
    private const val LEVEL_TASK_URL = "$BASE_URL/qqlevel/trpc/levelTask/Refresh"
    private const val SIGN_IN_URL = "$BASE_URL/hybrid-h5/api/json/daily_attendance/SignIn"
    private const val SIGN_IN_PAGE_URL = "$BASE_URL/hybrid-h5/api/json/daily_attendance/SignInMainPage"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/89.0.4389.72 MQQBrowser/6.2 Mobile Safari/537.36"

    /** 服务端在“今天已经签过”时仍会返回非 0，这些文案按成功处理。 */
    private val ALREADY_DONE_HINTS =
        listOf("已签", "已领", "已经", "重复", "already", "repeat")

    private val signCardOption = intOption(
        "sign_card_enabled",
        "日签卡",
        defaultValue = SWITCH_ON,
        desc = "QQ 资料卡每日签到（0.5 活跃天）。",
        options = listOf("关闭", "开启"),
    )

    private val collectCardOption = intOption(
        "collect_card_enabled",
        "收集卡",
        defaultValue = SWITCH_ON,
        desc = "QQ 每日打卡活动里的收集卡。",
        options = listOf("关闭", "开启"),
    )

    /** 触发方式，与群打卡一致：每天定时 / 启动后自动 / 仅手动。 */
    private val triggerOption = intOption(
        "trigger",
        "触发方式",
        defaultValue = TRIGGER_MANUAL,
        desc = "“每天定时”按每日执行时间运行；“启动后自动”在 QQ 启动后按启动后延迟运行；“仅手动”只响应“立即执行一次”。",
        options = listOf("每天定时", "启动后自动", "仅手动"),
    )

    private val timeOption = stringOption(
        "time",
        "每日执行时间",
        defaultValue = "08:00",
        desc = "24 小时制，格式 HH:mm；仅“每天定时”生效。",
        placeholder = "08:00",
    )

    private val startupDelayOption = sliderOption(
        "startup_delay",
        "启动后延迟",
        defaultValue = 30,
        desc = "仅“启动后自动”生效：QQ 启动后等待这么久再执行，避免启动阶段抢资源。",
        min = 5,
        max = 600,
        step = 5,
        suffix = "秒",
    )

    /**
     * 开关 / 单选值：未被显式持久化时回退到声明默认值。
     *
     * [TCQTSetting.getInt] 在“配置未注册”或“从未保存过”的路径上返回 0，
     * 而设置界面单选项显示的是声明默认值；不兜底就会出现“界面开启、执行判 0”。
     */
    private fun valueOr(option: Option<Int>): Int =
        if (option.isSet()) option.value else option.defaultValue

    /** 开关的可见状态：`开启(值=2,已保存)`，直接展示在卡片与执行结果里。 */
    private fun switchState(option: IntOption, value: Int): String {
        val label = option.options.getOrNull(value - 1) ?: "未知"
        val saved = if (option.isSet()) "已保存" else "未保存"
        return "$label(值=$value,$saved)"
    }

    /** 两个开关的实际读值与保存状态，供提示、卡片描述和诊断日志共用。 */
    private fun configSummary(): String =
        "日签卡=${switchState(signCardOption, signCard)}" +
            " 收集卡=${switchState(collectCardOption, collectCard)}"

    private val signCard: Int get() = valueOr(signCardOption)
    private val collectCard: Int get() = valueOr(collectCardOption)
    private val trigger: Int get() = valueOr(triggerOption)
    private val startupDelay: Int get() = valueOr(startupDelayOption)
    private val time: String
        get() = if (timeOption.isSet()) timeOption.value else timeOption.defaultValue

    /** 今日是否已自动执行过，避免定时触发反复重试。 */
    private var attemptDay by stringOption("attempt_day", "自动执行日期", isHide = true)

    /** 上次执行结果，展示在设置页说明里。 */
    private var lastResult by stringOption("last_result", "上次执行结果", isHide = true)

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var started = false

    @Volatile
    private var running = false

    /** 本次进程启动后是否还需要执行“启动后自动”。 */
    @Volatile
    private var startupPending = false

    /** “启动后自动”的到点时刻（毫秒时间戳）。 */
    @Volatile
    private var startupAt = 0L

    private val tickRunner = object : Runnable {
        override fun run() {
            try {
                checkSchedule()
            } finally {
                if (started) handler.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun install() {
        if (started) return
        started = true
        startupPending = true
        startupAt = System.currentTimeMillis() + startupDelay * 1000L
        Log.i("QQ 等级任务已加载，触发方式：${triggerLabel()}")
        handler.postDelayed(tickRunner, FIRST_DELAY_MS)
    }

    override fun onUiClick(context: Context): Boolean {
        return try {
            if (running) {
                // 手动点击必须有反馈，不受任何开关影响。
                QQToastCompat.info("日签卡 / 收集卡正在执行，请稍候")
                diag("点击被挡：running=true")
                return true
            }
            if (!QQInterfaces.isLogin) {
                QQToastCompat.error("QQ 未登录，无法执行")
                diag("点击失败：QQ 未登录")
                return true
            }
            QQToastCompat.info("开始执行：${configSummary()}")
            diag("手动触发：${configSummary()} 触发=${triggerLabel()}")
            runAll(auto = false, manual = true)
            true
        } catch (t: Throwable) {
            Log.e("QQ 等级任务：点击处理异常", t)
            diag("点击异常：${t.message}")
            QQToastCompat.error("执行失败：${t.message ?: t::class.java.simpleName}")
            true
        }
    }

    override fun getSettingDesc(key: String): String? {
        val state = "当前：${configSummary()}；触发：${triggerLabel()}"
        return if (lastResult.isBlank()) {
            "$desc\n$state"
        } else {
            "$desc\n$state\n上次执行：$lastResult"
        }
    }

    private fun checkSchedule() {
        if (!isEnabled()) return
        if (running || !QQInterfaces.isLogin) return

        when (trigger) {
            TRIGGER_DAILY -> {
                val target = parseTime() ?: run {
                    Log.e("每日执行时间格式无效：$time")
                    return
                }
                if (LocalTime.now().isBefore(target)) return
                if (attemptDay == today()) return
                runAll(auto = true, manual = false)
            }

            TRIGGER_STARTUP -> {
                if (!startupPending) return
                if (System.currentTimeMillis() < startupAt) return
                startupPending = false
                runAll(auto = true, manual = false)
            }
        }
    }

    private fun runAll(auto: Boolean, manual: Boolean = false) {
        if (running) return
        running = true
        ModuleScope.launchIO(TAG) {
            try {
                val uin = runCatching { QQInterfaces.currentUin }.getOrNull().orEmpty()
                if (uin.isBlank()) {
                    val text = "QQ 未登录，无法执行"
                    lastResult = "${timeLabel()} $text"
                    if (manual) QQToastCompat.error(text)
                    diag(text)
                    return@launchIO
                }

                val parts = mutableListOf<String>()
                var failed = false
                val cfg = configSummary()
                diag("读取配置：$cfg 触发=${triggerLabel()}")
                if (signCard == SWITCH_ON) {
                    val (ok, msg) = runSignCard(uin)
                    if (!ok) failed = true
                    parts += "日签卡：${if (ok) "完成" else "失败（$msg）"}"
                }
                if (collectCard == SWITCH_ON) {
                    val (ok, msg) = runCollectCard(uin)
                    if (!ok) failed = true
                    parts += "收集卡：${if (ok) "完成" else "失败（$msg）"}"
                }
                if (parts.isEmpty()) {
                    val text = "未开启任何任务（$cfg）"
                    lastResult = "${timeLabel()} $text"
                    if (manual) QQToastCompat.info(text)
                    diag(text)
                    return@launchIO
                }

                val text = parts.joinToString("；")
                lastResult = "${timeLabel()} $text｜$cfg"
                diag("结束：$text｜$cfg")
                if (failed) QQToastCompat.error(text) else QQToastCompat.success(text)
                if (auto) attemptDay = today()
            } catch (t: Throwable) {
                Log.e("QQ 等级任务执行异常", t)
                val text = "执行异常：${t.message ?: t::class.java.simpleName}"
                lastResult = "${timeLabel()} $text｜${configSummary()}"
                if (manual) QQToastCompat.error(text)
                diag(text)
            } finally {
                running = false
            }
        }
    }

    /** 日签卡：SignIn(type=1) + 等级任务领取（task_id=3）。 */
    private fun runSignCard(uin: String): Pair<Boolean, String> {
        return try {
            val bkn = bkn()
            if (bkn.isBlank()) return false to "未取到 ti.qq.com 的 pskey"
            val signIn = api(
                "$SIGN_IN_URL?bkn=$bkn",
                """{"uin":"$uin","type":1,"qua":"${qua()}"}""",
            )
            val ret = signIn.optInt("ret", -1)
            if (ret != 0 && !alreadyDone(signIn)) return false to describe(signIn)

            // 等级任务（0.5 活跃天）只是额外奖励，接口失败不应把日签卡判为失败。
            val refresh = runCatching {
                api("$LEVEL_TASK_URL?bkn=$bkn", """{"task_id":"3","mode":1}""")
            }.getOrNull()
            val code = refresh?.optJSONObject("response")
                ?.optJSONObject("result")
                ?.optInt("code", 0) ?: refresh?.optJSONObject("data")?.optInt("code", 0) ?: 0
            if (refresh != null && code != 0) {
                Log.w("日签卡等级任务领取未成功：${describe(refresh)}")
            }
            true to "ok"
        } catch (t: Throwable) {
            Log.e("日签卡执行异常", t)
            false to (t.message ?: t::class.java.simpleName)
        }
    }

    /** 收集卡：先取 subType，再 SignIn(type=4)。 */
    private fun runCollectCard(uin: String): Pair<Boolean, String> {
        return try {
            val bkn = bkn()
            if (bkn.isBlank()) return false to "未取到 ti.qq.com 的 pskey"
            val page = api(
                "$SIGN_IN_PAGE_URL?bkn=$bkn",
                """{"uin":"$uin","QYY":2,"qua":"${qua()}"}""",
            )
            if (page.optInt("ret", -1) != 0) return false to describe(page)

            val values = page.optJSONObject("data")
                ?.optJSONObject("vecSignInfo")
                ?.optJSONArray("value")
            val card = values?.optJSONObject(2)
                ?: values?.let { arr ->
                    (0 until arr.length())
                        .mapNotNull { arr.optJSONObject(it) }
                        .firstOrNull { it.has("subType") }
                }
            val subType = card?.opt("subType")
            if (subType == null) return false to "未取到收集卡 subType"

            val signIn = api(
                "$SIGN_IN_URL?bkn=$bkn",
                """{"uin":"$uin","type":4,"subType":$subType,"qua":"${qua()}"}""",
            )
            val ret = signIn.optInt("ret", -1)
            if (ret != 0 && !alreadyDone(signIn)) return false to describe(signIn)
            true to "ok"
        } catch (t: Throwable) {
            Log.e("收集卡执行异常", t)
            false to (t.message ?: t::class.java.simpleName)
        }
    }

    /** 今天已经签过 / 领过时服务端会返回非 0，这类回包按成功处理。 */
    private fun alreadyDone(resp: JSONObject): Boolean {
        val msg = resp.optString("msg") + resp.optString("message")
        return msg.isNotBlank() && ALREADY_DONE_HINTS.any { msg.contains(it, ignoreCase = true) }
    }

    private fun api(url: String, body: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Cookie", TicketManager.getCookie(DOMAIN))
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            diag("HTTP $code $url -> ${text.take(200)}")
            Log.i("$TAG HTTP $code ${url.substringBefore('?')} -> ${text.take(300)}")
            if (text.isBlank()) {
                return JSONObject().put("ret", -1).put("msg", "HTTP $code 空响应")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(resp: JSONObject): String {
        val msg = resp.optString("msg").ifBlank { resp.optString("message") }
        val code = resp.optInt("ret", resp.optInt("code", -1))
        return if (msg.isBlank()) "ret=$code" else "ret=$code $msg"
    }

    private fun qua(): String = runCatching { BaseApplication.getContext().qua }.getOrDefault("")

    private fun bkn(): String {
        val pskey = runCatching { TicketManager.getPskey(DOMAIN) }.getOrNull().orEmpty()
        if (pskey.isBlank()) {
            Log.w("$TAG 未取到 $DOMAIN 的 pskey，跳过本次请求")
            diag("未取到 $DOMAIN 的 pskey")
            return ""
        }
        var b = 5381
        for (c in pskey) {
            b += (b shl 5) + c.code
            b = b and 0x7FFFFFFF
        }
        return b.toString()
    }

    private fun triggerLabel(): String = when (trigger) {
        TRIGGER_DAILY -> "每天定时 $time"
        TRIGGER_STARTUP -> "启动后自动（延迟 ${startupDelay} 秒）"
        else -> "仅手动"
    }

    private fun parseTime(): LocalTime? = runCatching {
        val parts = time.trim().split(":")
        LocalTime.of(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
    }.getOrNull()

    private fun today(): String = LocalDate.now().toString()

    private fun isEnabled(): Boolean = TCQTSetting.getValue<Boolean>(key) ?: false

    private fun timeLabel(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun diag(text: String) {
        runCatching {
            val file = File(HookEnv.moduleDataPath, "qq_level_tasks_diag.log")
            file.parentFile?.mkdirs()
            if (file.length() > 256 * 1024) file.delete()
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            file.appendText("$stamp $text\n", Charsets.UTF_8)
        }
    }
}
