package com.owo233.tcqt.ui.parasitic

import android.content.Context
import com.owo233.tcqt.core.env.HookEnv

/** 系统类走 boot，Compose/AndroidX/模块状态走模块；仅缺失类才交给当前宿主 loader。 */
internal class ModuleActivityClassLoader(private val module: ClassLoader) :
    ClassLoader(Context::class.java.classLoader) {

    override fun findClass(name: String): Class<*> = try {
        module.loadClass(name)
    } catch (_: ClassNotFoundException) {
        HookEnv.hostClassLoader.loadClass(name)
    }
}
