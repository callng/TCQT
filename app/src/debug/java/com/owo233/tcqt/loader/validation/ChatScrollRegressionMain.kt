package com.owo233.tcqt.loader.validation

import android.os.Looper
import com.owo233.tcqt.core.config.ConfigRestorePlan
import com.owo233.tcqt.core.config.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** ADB-only storage diagnostics; does not send messages or change injection modes. */
object ChatScrollRegressionMain {

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2)
        val directory = File(args[0])
        require(directory.absolutePath ==
            "/data/data/com.tencent.mobileqq/files/5463306EE50FE3AA/TCQT/global/setting" ||
            directory.absolutePath.startsWith("/data/local/tmp/tcqt-refactor-tests/"))
        @Suppress("DEPRECATION")
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val store = SettingsStore(directory, "TCQT")
        val report = JSONObject().put("command", args[1])
        when (args[1]) {
            "inspect" -> {
                report.put("keys", store.all().size)
                listOf("default_vas_attrs", "default_vas_attrs.type", "repeat_message",
                    "repeat_message.options", "show_msg_info", "sticky_search_bar").forEach {
                    report.put(it, store[it])
                }
            }
            "toggle" -> {
                require(args[2] in setOf("default_vas_attrs", "show_msg_info", "repeat_message"))
                store.put(args[2], args[3].toBooleanStrict())
                report.put(args[2], store[args[2]])
            }
            "import" -> {
                require(args[2].startsWith("/data/local/tmp/tcqt-refactor-tests/"))
                val text = File(args[2]).inputStream().use(ConfigRestorePlan::read)
                val entries = JSONObject(text).getJSONArray("settings")
                val schema = (0 until entries.length()).associate {
                    val item = entries.getJSONObject(it)
                    item.getString("key") to item.getString("type")
                }
                val plan = ConfigRestorePlan.decode(text, schema)
                store.update { it.putAll(plan.values) }
                check(plan.values.all { (key, value) -> store[key] == value })
                report.put("verifiedSettings", plan.values.size)
            }
            "benchmark" -> {
                val key = "default_vas_attrs.type"
                val expected = store[key]
                repeat(100) { check(store[key] == expected) }
                val timings = (0 until 5).map {
                    val start = System.nanoTime()
                    repeat(2_000) { check(store[key] == expected) }
                    (System.nanoTime() - start) / 1_000_000.0
                }
                report.put("readsPerSample", 2_000).put("readMs", JSONArray(timings))
            }
            else -> error("Unknown diagnostic command")
        }
        println(report)
        kotlin.system.exitProcess(0)
    }
}
