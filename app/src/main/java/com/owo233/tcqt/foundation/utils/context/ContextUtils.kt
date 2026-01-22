package com.owo233.tcqt.foundation.utils.context

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.owo233.tcqt.foundation.utils.log.Log
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

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

    fun getContextCreateMethod(
        loadParam: XC_LoadPackage.LoadPackageParam
    ): Method? {
        val className = loadParam.appInfo?.name ?: return fallbackContextWrapper()

        val clz = runCatching {
            loadParam.classLoader.loadClass(className)
        }.getOrElse {
            Log.e("getContextCreateMethod: Failed to load class $className", it)
            return fallbackContextWrapper()
        }

        return findInClassOrSuper(clz)
            ?: fallbackContextWrapper()
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

    private fun findInClassOrSuper(clz: Class<*>): Method? {
        return findMethodInClass(clz)
            ?: clz.superclass?.let { findMethodInClass(it) }
    }

    private fun findMethodInClass(clz: Class<*>?): Method? =
        clz?.let { cls ->
            cls.getDeclaredMethodOrNull("attachBaseContext", Context::class.java)
                ?: cls.getDeclaredMethodOrNull("onCreate")
        }

    private fun fallbackContextWrapper(): Method? =
        ContextWrapper::class.java
            .getDeclaredMethodOrNull("attachBaseContext", Context::class.java)
            .also {
                if (it == null) {
                    Log.e("fallbackContextWrapper: ContextWrapper.attachBaseContext not found")
                }
            }

    private fun Class<*>.getDeclaredMethodOrNull(
        name: String,
        vararg params: Class<*>
    ): Method? = runCatching {
        getDeclaredMethod(name, *params)
    }.getOrNull()
}
