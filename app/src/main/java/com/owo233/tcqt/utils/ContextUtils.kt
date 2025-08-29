package com.owo233.tcqt.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application

internal object ContextUtils {

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun getCurrentActivity(): Activity? {
        return try {
            val activityThreadClass = Class.forName(
                "android.app.ActivityThread",
                false,
                Application::class.java.classLoader
            )
            val activityThread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null)

            val activitiesField = activityThreadClass.getDeclaredField("mActivities").apply {
                isAccessible = true
            }
            val activities = activitiesField.get(activityThread) as Map<*, *>

            for (activityRecord in activities.values) {
                val activityRecordClass = activityRecord!!::class.java

                val pausedField = activityRecordClass.getDeclaredField("paused").apply {
                    isAccessible = true
                }
                if (!pausedField.getBoolean(activityRecord)) {
                    val activityField = activityRecordClass.getDeclaredField("activity").apply {
                        isAccessible = true
                    }
                    return activityField.get(activityRecord) as? Activity
                }
            }
            null
        } catch (e: Exception) {
            logE(msg = "getCurrentActivity error", cause = e)
            null
        }
    }
}
