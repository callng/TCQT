package com.owo233.tcqt.features.script

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.script.ScriptGateway
import com.owo233.tcqt.host.QQInterfaces

/**
 * 脚本管理页的打开入口。
 *
 * 宿主 QQ 设置页入口（`AddModuleEntrance`）与模块自身设置页入口（[ScriptEngine]）
 * 共用这一处逻辑：解析模块类加载器、切主线程、启动脚本管理页。
 */
internal object ScriptLauncher {

    /** 引擎未安装（引擎类未注册，例如非 NT 宿主）时提示并返回 false。 */
    fun open(context: Context?): Boolean {
        if (ScriptGateway.instance == null) {
            Toasts.error("脚本引擎未就绪")
            return false
        }

        val activity = context as? Activity ?: QQInterfaces.topActivity
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val loader = System.getProperties()["tcqt.module_class_loader"] as? ClassLoader
                    ?: javaClass.classLoader
                val clazz = loader.loadClass(ModuleComponents.SCRIPT_MANAGER_ACTIVITY)
                activity.startActivity(Intent(activity, clazz))
            }.onFailure {
                Log.e("脚本管理页打开失败", it)
                Toasts.error("脚本管理页打开失败")
            }
        }
        return true
    }
}
