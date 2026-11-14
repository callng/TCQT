package com.owo233.tcqt.host.service

import android.os.Process
import com.owo233.tcqt.core.env.HookEnv
import org.json.JSONObject
import java.io.File

/** Bounded local counters for the Android path; never a copy of game/API payloads. */
internal object FarmCustomFriendsDiagnostics {

    private const val PREFIX = "TCQT_FARM_FRIENDS_STATUS:"
    private val allowed = setOf(
        "hook_ready", "custom_session", "unmarked_script", "script_received", "script_rejected",
        "script_transformed", "script_failed", "adapter_started", "system_unavailable",
        "manager_ready", "manager_unsupported", "discovery_ready", "query_start", "brief_reply",
        "friends_reply", "query_complete", "query_failed",
    )
    private val events = JSONObject()

    fun accept(body: String): Boolean {
        if (body.length > 1024 || PREFIX !in body) return false
        return runCatching {
            val key = JSONObject(body).optString("key")
            if (!key.startsWith(PREFIX)) return false
            val status = JSONObject(key.removePrefix(PREFIX))
            val stage = status.optString("stage")
            if (stage !in allowed) return false
            record(stage, status.optInt("count", -1).takeIf { it in 0..100_000 })
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun record(stage: String, count: Int? = null) {
        if (stage !in allowed) return
        runCatching {
            if (stage == "custom_session") allowed.filter { it != "hook_ready" }.forEach(events::remove)
            events.put(stage, JSONObject().put("time", System.currentTimeMillis()).apply {
                if (count != null && count in 0..100_000) put("count", count)
            })
            val directory = File(HookEnv.hostAppContext.filesDir, "tcqt_farm_friends").apply { mkdirs() }
            File(directory, "status-${Process.myPid()}.json").writeText(
                JSONObject().put("revision", 2).put("pid", Process.myPid()).put("events", events).toString(),
            )
        }
    }
}
