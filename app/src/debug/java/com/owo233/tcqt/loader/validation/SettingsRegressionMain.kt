package com.owo233.tcqt.loader.validation

import com.owo233.tcqt.core.config.SettingsStore
import android.os.Looper
import io.fastkv.FastKV
import java.io.File

/** Runs isolated real-file tests through app_process; never opens host user settings. */
object SettingsRegressionMain {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2)
        val directory = File(args[0])
        require(directory.absolutePath.startsWith("/data/local/tmp/tcqt-refactor-tests/"))
        @Suppress("DEPRECATION")
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val store = SettingsStore(directory, "test")
        when (args[1]) {
            "legacy" -> {
                val kv = FastKV.Builder(directory.absolutePath, "test").build()
                kv.putBoolean("legacy.enabled", true)
                kv.putInt("legacy.number", 17)
                kv.putString("legacy.text", "配置兼容")
                kv.force()
                kv.close()
            }
            "writer" -> repeat(20) { index -> store.update {
                it["counter"] = (it["counter"] as? Int ?: 0) + 1
                it["${args[2]}.$index"] = true
            } }
            "verify" -> {
                check(store["legacy.enabled"] == true)
                check(store["legacy.number"] == 17)
                check(store["legacy.text"] == "配置兼容")
                check(store["counter"] == args[2].toInt()) { "counter=${store["counter"]}" }
                println("PASS legacy compatibility, cross-process writes, restart visibility: ${store.all().size} keys")
            }
            else -> error("Unknown settings test")
        }
        kotlin.system.exitProcess(0)
    }
}
