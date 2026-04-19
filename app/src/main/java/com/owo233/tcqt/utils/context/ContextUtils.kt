package com.owo233.tcqt.utils.context

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import com.owo233.tcqt.utils.log.Log

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object ContextUtils {

    @Throws(Exception::class)
    fun getCurrentActivity(): Activity {
        val activityThread = Class.forName(
            "android.app.ActivityThread",
            false,
            Application::class.java.classLoader
        ).getMethod("currentActivityThread").invoke(null)

        val activities = activityThread::class.java
            .getDeclaredField("mActivities")
            .apply { isAccessible = true }
            .get(activityThread) as Map<*, *>

        val record = activities.values
            .firstOrNull { record ->
                record!!::class.java
                    .getDeclaredField("paused")
                    .apply { isAccessible = true }
                    .getBoolean(record).not()
            }
            ?: throw IllegalStateException("No non-paused activity found")

        return record::class.java
            .getDeclaredField("activity")
            .apply { isAccessible = true }
            .get(record) as Activity
    }

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
