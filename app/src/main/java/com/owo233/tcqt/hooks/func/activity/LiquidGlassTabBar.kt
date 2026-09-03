// This feature is ported and modified from WeChat-LiquidGlass (originally written in Java)
// Now reimplemented in Kotlin with additional features.
// Source: https://github.com/liuran001/WeChat-LiquidGlass
// License: MIT, see THIRD_PARTY_LICENSES for full copyright and license text.

package com.owo233.tcqt.hooks.func.activity

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Build
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionPriority
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.func.liquidglass.GlassBarInstaller
import com.owo233.tcqt.hooks.func.liquidglass.QQTabLocator
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log

@RegisterAction
class LiquidGlassTabBar : IAction {

    override val key: String get() = "liquid_glass_tab_bar"
    override val name: String get() = "液态玻璃导航栏"
    override val desc: String get() = "使用液态玻璃导航栏替换 QQ 原生底部导航栏。"
    override val uiTab: String get() = "界面"
    override val priority: ActionPriority get() = ActionPriority.EARLY

    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                key = QQTabLocator.LIQUID_GLASS_CONFIG_KEY,
                name = "液态玻璃导航栏配置",
                desc = "放置其他可调整的配置",
                options = listOf("平滑切页(可能触发BUG)"),
            )
        )

    /** 折射管线依赖 RuntimeShader，最低 Android 13。 */
    override fun onInit(): Boolean {
        // 在 TIM 上 有些 BUG 但我们不做屏蔽处理
        return HookEnv.isNT() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    override fun onRun(app: Application, process: ActionProcess) {
        hookTabSwitch()
        hookActivityResume()
    }

    /**
     * 挂钩底栏的切换方法。
     *
     * 只挂钩底栏类自身声明的方法：若挂到基类，进程内所有同类控件
     * 都会被波及。新旧两套底栏并存在于同一安装包中，通常只有一套
     * 实际存在，缺失的一套按预期跳过。
     */
    private fun hookTabSwitch() {
        var hooked = 0
        for (className in QQTabLocator.tabViewClasses) {
            val cls = load(className) ?: continue
            val method = runCatching {
                cls.getDeclaredMethod(QQTabLocator.SWITCH_METHOD, Int::class.javaPrimitiveType)
            }.getOrNull()
            if (method == null) {
                Log.w("底栏类未声明 ${QQTabLocator.SWITCH_METHOD}(int): $className")
                continue
            }
            method.hookAfter { param ->
                val view = param.thisObject as? View ?: return@hookAfter
                val index = param.args.getOrNull(0) as? Int ?: return@hookAfter
                GlassBarInstaller.onTabChanged(view, index)
            }
            hooked++
        }
        if (hooked == 0) {
            Log.w("未能挂钩任何底栏切换方法，安装将仅依赖界面恢复轮询")
        }
    }

    /**
     * 挂钩 Activity 恢复回调。
     *
     * 主界面构建为异步过程，底栏可能在恢复后数秒才出现，此处触发
     * 限时轮询兜底；切页钩子才是首选的安装触发点。
     */
    private fun hookActivityResume() {
        Instrumentation::class.java
            .getMethod("callActivityOnResume", Activity::class.java)
            .hookAfter { param ->
                val activity = param.args.getOrNull(0) as? Activity ?: return@hookAfter
                if (activity.javaClass.name == QQTabLocator.LAUNCHER_ACTIVITY) {
                    GlassBarInstaller.scheduleInstall(activity)
                }
            }
    }
}
