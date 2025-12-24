package com.owo233.tcqt.utils.context

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import com.owo233.tcqt.utils.log.Log

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object ContextUtils {

    fun getCurrentActivity(): Activity? {
        return runCatching {
            val activityThread = Class.forName(
                "android.app.ActivityThread",
                false,
                Application::class.java.classLoader
            ).getMethod("currentActivityThread").invoke(null)

            val activities = activityThread::class.java
                .getDeclaredField("mActivities")
                .apply { isAccessible = true }
                .get(activityThread) as? Map<*, *>

            activities?.values
                ?.firstOrNull { record ->
                    record!!::class.java
                        .getDeclaredField("paused")
                        .apply { isAccessible = true }
                        .getBoolean(record).not()
                }
                ?.let { record ->
                    record::class.java
                        .getDeclaredField("activity")
                        .apply { isAccessible = true }
                        .get(record) as? Activity
                }
        }.onFailure {
            Log.e("getCurrentActivity: Failed to get current activity", it)
        }.getOrNull()
    }

    fun getCurApplication(): Application? {
        return tryGetApplication("android.app.ActivityThread", "currentApplication")
            ?: tryGetApplication("android.app.AppGlobals", "getInitialApplication")
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