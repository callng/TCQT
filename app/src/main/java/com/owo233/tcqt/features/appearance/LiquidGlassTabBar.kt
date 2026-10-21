// Ported and modified from WeChat-LiquidGlass
// Source: https://github.com/liuran001/WeChat-LiquidGlass
// License: MIT, see THIRD_PARTY_LICENSES for full copyright and license text.

package com.owo233.tcqt.features.appearance

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.appearance.liquidglass.BottomBarImplementation
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBottomBarConfigStore
import com.owo233.tcqt.features.appearance.liquidglass.GlassBarInstaller
import com.owo233.tcqt.features.appearance.liquidglass.NewViewBarInstaller
import com.owo233.tcqt.features.appearance.liquidglass.QQTabLocator

@RegisterAction
object LiquidGlassTabBar : Feature(
    key = "liquid_glass_tab_bar",
    name = "悬浮底栏",
    desc = "使用悬浮底栏替换 QQ 原生底部导航栏。",
    priority = ActionPriority.CRITICAL,
    /**
     * 复合条件无法拆成独立字段，故用 `extraCondition`：
     * `HookEnv.isNT() && (新视图实现 || SDK >= TIRAMISU)`。
     *
     * 经典实现的折射管线依赖 RuntimeShader；新视图的普通模式可在更低版本工作。
     * TIM 上有已知 BUG，不做屏蔽，只保留 NT 判断。
     */
    requires = Requires(
        ntOnly = true,
        extraCondition = {
            val config = FloatingBottomBarConfigStore.read()
            config.implementation == BottomBarImplementation.NEW_VIEW ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        },
    ),
) {

    // 声明顺序 = 设置界面的渲染顺序。
    // 派生 key 必须逐个等于 FloatingBottomBarConfigStore / QQTabLocator 里的常量
    // （由 DerivedKeyCompatibilityTest 钉住）。
    private val implementation by intOption(
        settingKey = "implementation",
        name = "底栏样式",
        defaultValue = FloatingBottomBarConfigStore.DEFAULT_IMPLEMENTATION,
        options = listOf("经典", "新视图"),
    )

    private val mode by intOption(
        settingKey = "mode",
        name = "渲染模式",
        defaultValue = FloatingBottomBarConfigStore.DEFAULT_MODE,
        options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("普通", "液态玻璃")
        } else {
            listOf("普通")
        },
    )

    private val scale by sliderOption(
        settingKey = "scale",
        name = "悬浮底栏缩放",
        defaultValue = FloatingBottomBarConfigStore.DEFAULT_SCALE_PERCENT,
        min = 80,
        max = 120,
        step = 10,
        suffix = "%",
    )

    private val blurPercent by sliderOption(
        settingKey = "blur_percent",
        name = "背景模糊",
        defaultValue = FloatingBottomBarConfigStore.DEFAULT_BLUR_PERCENT,
        min = 0,
        max = 100,
        step = 25,
        suffix = "%",
        isHide = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
    )

    private val position by intOption(
        settingKey = "position",
        name = "底栏位置",
        defaultValue = FloatingBottomBarConfigStore.DEFAULT_POSITION,
        options = listOf("适中", "靠底"),
    )

    private val config by multiIntOption(
        settingKey = "config",
        name = "悬浮底栏配置",
        desc = "放置其他可调整的配置",
        options = listOf("平滑切页"),
    )

    override fun install() {
        hookTabSwitch()
        hookActivityResume()
        hookThemeChange()
    }

    private fun hookThemeChange() {
        val fragment = load("com.tencent.mobileqq.app.FrameFragment") ?: return
        // QQ re-applies the native tab layout while changing skins. Let it operate on
        // the original hierarchy, then measure and install against the refreshed views.
        runCatching { fragment.getDeclaredMethod("onPreThemeChanged") }.getOrNull()
            ?.hookBefore { NewViewBarInstaller.onHostThemeChanging() }
        val postThemeChange = runCatching { fragment.getDeclaredMethod("onPostThemeChanged") }.getOrNull()
        // Also cover hosts which dispatch only the post callback.
        postThemeChange?.hookBefore { NewViewBarInstaller.onHostThemeChanging() }
        postThemeChange?.hookAfter { NewViewBarInstaller.onHostThemeChanged() }
    }

    /**
     * 挂钩底栏的切换方法。
     *
     * 只挂钩底栏类自身声明的方法：挂到基类会波及进程内所有同类控件。
     * 新旧两套底栏并存于同一安装包，通常只有一套实际存在，缺失的一套跳过。
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
            method.hookBefore { param ->
                val index = param.args.getOrNull(0) as? Int ?: return@hookBefore
                QQTabLocator.armSmoothTarget(index)
            }
            method.hookAfter { param ->
                QQTabLocator.clearSmoothTarget()
                val view = param.thisObject as? View ?: return@hookAfter
                val index = param.args.getOrNull(0) as? Int ?: return@hookAfter
                if (FloatingBottomBarConfigStore.read().implementation == BottomBarImplementation.NEW_VIEW) {
                    NewViewBarInstaller.onTabChanged(view, index)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    GlassBarInstaller.onTabChanged(view, index)
                }
            }
            hooked++
        }
        if (hooked == 0) {
            Log.w("未能挂钩任何底栏切换方法，安装将仅依赖界面恢复轮询")
        }
    }

    /**
     * 挂钩 Activity 恢复回调：主界面异步构建，底栏可能数秒后才出现，
     * 此处触发限时轮询兜底；底栏切换钩子才是首选的安装触发点。
     */
    private fun hookActivityResume() {
        Instrumentation::class.java
            .getMethod("callActivityOnResume", Activity::class.java)
            .hookAfter { param ->
                val activity = param.args.getOrNull(0) as? Activity ?: return@hookAfter
                if (activity.javaClass.name == QQTabLocator.LAUNCHER_ACTIVITY) {
                    if (FloatingBottomBarConfigStore.read().implementation == BottomBarImplementation.NEW_VIEW) {
                        NewViewBarInstaller.scheduleInstall(activity)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        GlassBarInstaller.scheduleInstall(activity)
                    }
                }
            }
    }
}
