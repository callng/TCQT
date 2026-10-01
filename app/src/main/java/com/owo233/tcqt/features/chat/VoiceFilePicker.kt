package com.owo233.tcqt.features.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.ui.parasitic.DynamicActivityRegistry

/**
 * 调起系统文件选择器并接收结果。
 *
 * 借助 TCQT 的寄生 Activity 机制：在宿主进程内起一个真正的
 * [VoicePickerActivity]（ComponentActivity），它选完文件后经 [deliver] 回调回来。
 * 全程不跳出宿主应用。
 */
internal object VoiceFilePicker {

    @Volatile
    private var callback: ((Uri?) -> Unit)? = null

    fun pick(context: Context, onResult: (Uri?) -> Unit) {
        val activity = context.findActivity()
        if (activity == null) {
            Toasts.error("找不到 Activity，无法打开选择器")
            return
        }
        callback = onResult
        runCatching {
            val loader = System.getProperties()["tcqt.module_class_loader"] as? ClassLoader
                ?: this.javaClass.classLoader
            val clazz = loader.loadClass("com.owo233.tcqt.features.chat.VoicePickerActivity")
            // 让 ParasiticActivity 识别为可寄生的 Activity
            DynamicActivityRegistry.register(clazz)
            activity.startActivity(Intent(activity, clazz))
        }.onFailure {
            callback = null
            Log.e("VoiceFilePicker: 启动选择器失败", it)
            Toasts.error("打开文件选择器失败：${it.message}")
        }
    }

    /** 由 [VoicePickerActivity] 回传结果。 */
    fun deliver(uri: Uri?) {
        val cb = callback
        callback = null
        cb?.invoke(uri)
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c != null) {
            if (c is Activity) return c
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }
}
