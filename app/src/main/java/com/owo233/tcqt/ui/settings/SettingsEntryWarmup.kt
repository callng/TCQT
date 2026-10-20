package com.owo233.tcqt.ui.settings

import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import java.util.concurrent.atomic.AtomicBoolean

/** 只在用户进入宿主设置后预热；不创建 Activity、Composition 或配置草稿。 */
internal object SettingsEntryWarmup {

    private val scheduled = AtomicBoolean()

    fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            // 等宿主设置页本轮消息和绘制完成，再把类验证与目录转换交给后台。
            Looper.myQueue().addIdleHandler {
                ModuleScope.launchIO("TCQT-SettingsWarmup") {
                    runCatching {
                        val activityClass = HookEnv.moduleClassLoader
                            .loadClass(ModuleComponents.SETTINGS_ACTIVITY)
                        activityClass.getDeclaredConstructor()
                        activityClass.declaredMethods
                        FeatureCatalog.getAllFeatures()
                    }.onFailure {
                        scheduled.set(false)
                        Log.w("设置页预热失败，打开时将正常初始化", it)
                    }
                }
                false
            }
        }
    }
}
