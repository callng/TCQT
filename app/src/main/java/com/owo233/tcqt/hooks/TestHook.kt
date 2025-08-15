package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.logI

// @RegisterAction
class TestHook: AlwaysRunAction() {

    /**
     * 仅供测试的hook，在发布版本时必须移除@RegisterAction注解
     */
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("android.app.Instrumentation")
            ?.hookMethod(
                "execStartActivity",
                Context::class.java,
                android.os.IBinder::class.java,
                android.os.IBinder::class.java,
                Activity::class.java,
                Intent::class.java,
                Int::class.java,
                android.os.Bundle::class.java,
                beforeHook { param ->
                    val intent = param.args[4] as Intent
                    val comp = intent.component
                    val className = comp?.className

                    logI(msg = "启动 Intent: $className")

                    intent.extras?.let { extras ->
                        for (key in extras.keySet()) {
                            val value = extras.get(key)
                            logI(msg = "Extra: $key = $value (${value?.javaClass?.name})")
                        }
                    }
                }
            )
    }

    override val name: String get() = "仅供测试"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
