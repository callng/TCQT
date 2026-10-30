package com.owo233.tcqt.ui.parasitic

import android.content.Intent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal class ActivityManagerHandler(val base: Any) : InvocationHandler, TCQTProxyMarker {

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        when (method.name) {
            "startActivity", "startActivityAsUser", "startActivityAsCaller",
            "startActivityAndWait", "startActivityWithConfig", "startActivityWithFeature" -> {
                val index = args?.indexOfFirst { it is Intent } ?: -1
                if (index >= 0) args!![index] = ActivityProxyIntents.wrap(args[index] as Intent)
            }
            "startActivities" -> {
                val index = args?.indexOfFirst { it is Array<*> && it.isArrayOf<Intent>() } ?: -1
                if (index >= 0) {
                    @Suppress("UNCHECKED_CAST")
                    val intents = args!![index] as Array<Intent>
                    args[index] = intents.map(ActivityProxyIntents::wrap).toTypedArray()
                }
            }
        }
        return try {
            method.invoke(base, *args.orEmpty())
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
