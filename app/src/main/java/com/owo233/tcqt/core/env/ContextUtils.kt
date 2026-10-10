package com.owo233.tcqt.core.env

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import com.owo233.tcqt.core.log.Log

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object ContextUtils {

    /**
     * Returns the current non-paused Activity when one is available.
     *
     * During process start and while the host is in the background there may
     * be no resumed Activity. Reflection can also fail when Android changes
     * the private ActivityThread fields, so callers must treat a null result
     * as a normal transient state.
     */
    fun getCurrentActivity(): Activity? = runCatching {
        val activityThread = Class.forName(
            "android.app.ActivityThread",
            false,
            Application::class.java.classLoader
        ).getMethod("currentActivityThread").invoke(null)

        val activities = activityThread::class.java
            .getDeclaredField("mActivities")
            .apply { isAccessible = true }
            .get(activityThread) as Map<*, *>

        val record = activities.values.firstOrNull { record ->
            record != null && record::class.java
                .getDeclaredField("paused")
                .apply { isAccessible = true }
                .getBoolean(record).not()
        } ?: return@runCatching null

        record::class.java
            .getDeclaredField("activity")
            .apply { isAccessible = true }
            .get(record) as? Activity
    }.getOrNull()

    fun getCurApplication(): Application {
        return tryGetApplication("android.app.ActivityThread", "currentApplication")
            ?: tryGetApplication("android.app.AppGlobals", "getInitialApplication")
            ?: throw IllegalStateException("Failed to get current application")
    }

    private fun tryGetApplication(className: String, methodName: String): Application? {
        return runCatching {
            Class.forName(className)
                .getDeclaredMethod(methodName)
                .apply { isAccessible = true }
                .invoke(null) as? Application
        }.onFailure {
            Log.e("getCurApplication: $className.$methodName failed", it)
        }.getOrNull()
    }
}
