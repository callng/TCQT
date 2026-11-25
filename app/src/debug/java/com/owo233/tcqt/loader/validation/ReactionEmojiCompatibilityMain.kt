package com.owo233.tcqt.loader.validation

import android.annotation.SuppressLint
import com.owo233.tcqt.features.menu.DisableReactionLimit
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod

/** Runs the production matchers on APK DEX fixtures without starting QQ. */
object ReactionEmojiCompatibilityMain {
    // Each invocation is a separate app_process testing one archive with one bridge.
    @SuppressLint("UnsafeDynamicallyLoadedCode", "DuplicateCreateDexKit")
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected libdexkit.so and DEX archive paths" }
        System.load(args[0])
        val report = JSONObject()
        DexKitBridge.create(args[1]).use { bridge ->
            val hasEsSwitch = bridge.findClass {
                matcher { className("com.tencent.qqnt.aio.emoReply.AioEsReactionSwitch") }
            }.isNotEmpty()
            DisableReactionLimit.getQueryMap().forEach { (key, query) ->
                val matches = bridge.findMethod(query as FindMethod)
                if (key == "reaction_es_switch" && !hasEsSwitch) {
                    check(matches.isEmpty()) { "$key: unexpected matches $matches" }
                } else {
                    check(matches.size == 1) { "$key: expected one match, got $matches" }
                }
                if (key == "reaction_more_panel_filter") {
                    check(matches.single().paramTypeNames.toSet() == setOf("int", "java.util.List"))
                }
                report.put(key, JSONArray(matches.map { it.descriptor }))
            }
        }
        println(report.put("passed", true))
    }
}
