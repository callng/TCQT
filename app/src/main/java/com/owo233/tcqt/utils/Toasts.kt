package com.owo233.tcqt.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.hooks.base.hostInfo
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object Toasts {
    const val TYPE_PLAIN = -1
    const val TYPE_INFO = 0
    const val TYPE_ERROR = 1
    const val TYPE_SUCCESS = 2

    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    private var clazzQQToast: Class<*>? = null
    private var methodToastShow: Method? = null
    private var methodToastMakeText: Method? = null

    private fun initQQToast() {
        if (clazzQQToast != null) return
        clazzQQToast = XpClassLoader.load("com.tencent.mobileqq.widget.QQToast")
            ?: findQQToastFallback()
        clazzQQToast?.let {
            methodToastShow = it.methods.firstOrNull { m ->
                m.returnType == Toast::class.java && m.parameterTypes.isEmpty()
            }
            methodToastMakeText = findMethod(it, "a", "b", "makeText")
        }
    }

    private fun findQQToastFallback(): Class<*>? {
        XpClassLoader.load("com.tencent.mobileqq.activity.aio.doodle.DoodleLayout")?.let { clz ->
            return clz.declaredFields
                .map(Field::getType)
                .firstOrNull { !it.isPrimitive && !it.isInterface && !View::class.java.isAssignableFrom(it) }
        }
        return XpClassLoader.load("com.tencent.qqmini.sdk.core.widget.QQToast")
    }

    private fun findMethod(clazz: Class<*>, vararg names: String): Method? {
        for (name in names) {
            try {
                return clazz.getMethod(name, Context::class.java, Int::class.javaPrimitiveType, CharSequence::class.java, Int::class.javaPrimitiveType)
            } catch (_: NoSuchMethodException) { }
        }
        return null
    }

    private fun showToast(context: Context?, type: Int, text: CharSequence, duration: Int) {
        val ctx = context ?: hostInfo.application
        SyncUtils.runOnUiThread {
            if (type == TYPE_PLAIN) {
                Toast.makeText(ctx, text, duration).show()
                return@runOnUiThread
            }
            runCatching {
                initQQToast()
                val qqToastObj = methodToastMakeText?.invoke(null, ctx, type, text, duration)
                methodToastShow?.invoke(qqToastObj)
                    ?: Toast.makeText(ctx, text, duration).show()
            }.onFailure {
                Log.e("Toasts error", it)
                Toast.makeText(ctx, text, duration).show()
            }
        }
    }

    @JvmStatic fun info(ctx: Context, text: CharSequence, duration: Int = LENGTH_SHORT) =
        showToast(ctx, TYPE_INFO, text, duration)

    @JvmStatic fun success(ctx: Context, text: CharSequence, duration: Int = LENGTH_SHORT) =
        showToast(ctx, TYPE_SUCCESS, text, duration)

    @JvmStatic fun error(ctx: Context, text: CharSequence, duration: Int = LENGTH_SHORT) =
        showToast(ctx, TYPE_ERROR, text, duration)

    @JvmStatic fun show(ctx: Context, text: CharSequence, duration: Int = LENGTH_SHORT) =
        showToast(ctx, TYPE_PLAIN, text, duration)

    @JvmStatic fun show(text: CharSequence) =
        showToast(null, TYPE_PLAIN, text, LENGTH_SHORT)
}
