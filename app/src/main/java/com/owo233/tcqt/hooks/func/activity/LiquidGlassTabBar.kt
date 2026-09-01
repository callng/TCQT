/**
 * 此功能由 QFun 提供
 * https://github.com/oneQAQone/QFun
 */
package com.owo233.tcqt.hooks.func.activity

import android.app.Activity
import android.app.Application
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ancestors
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionPriority
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.func.liquidglass.LiquidGlassLifecycleOwner
import com.owo233.tcqt.hooks.func.liquidglass.LiquidGlassTabBarContent
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.callMethod
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObjectByTypeOrNull
import com.owo233.tcqt.utils.reflect.getObjectOrNull
import java.lang.reflect.Method

@RegisterAction
class LiquidGlassTabBar : IAction {

    override val key: String get() = "liquid_glass_tab_bar"
    override val name: String get() = "液态玻璃导航栏"
    override val desc: String get() = "用 Compose 液态玻璃导航栏替换 QQ 原生底部导航栏。"
    override val uiTab: String get() = "界面"
    override val priority: ActionPriority get() = ActionPriority.CRITICAL

    private companion object {
        const val VIEW_TAG = "TCQT_LiquidGlassTabBar"

        const val BADGE_NUM_RED = 2
        const val BADGE_TEXT_RED = 4

        const val TAB_FRAME_LAYOUT = "com.tencent.mobileqq.tab.TabFrameLayout"
        const val QQ_TAB_LAYOUT = "com.tencent.mobileqq.widget.QQTabLayout"
        const val FRAME_FRAGMENT = "com.tencent.mobileqq.app.FrameFragment"
        const val BASE_ACTIVITY = "com.tencent.mobileqq.app.BaseActivity"
        const val SPLASH_ACTIVITY = "com.tencent.mobileqq.activity.SplashActivity"
        const val QUI_BADGE = "com.tencent.mobileqq.quibadge.QUIBadge"
        const val QUI_BLUR_VIEW_WRAPPER = "com.tencent.qui.quiblurview.QQBlurViewWrapper"
        const val RECYCLER_VIEW_ADAPTER = $$"androidx.recyclerview.widget.RecyclerView$Adapter"
        const val UNITED_CONFIG_IMPL = "com.tencent.mobileqq.unitedconfig_android.api.impl.UnitedConfigManagerImpl"
    }

    private var lifecycleOwner: LiquidGlassLifecycleOwner? = null
    private var nativeTabLayout: Any? = null
    private val tabTags = mutableStateListOf<String>()
    private val currentTabTag = mutableStateOf("")
    private val tabBadgeTexts = mutableStateMapOf<Int, String>()

    private val lifecycleEvents = mapOf(
        "doOnCreate" to Lifecycle.Event.ON_CREATE,
        "doOnStart" to Lifecycle.Event.ON_START,
        "doOnResume" to Lifecycle.Event.ON_RESUME,
        "doOnPause" to Lifecycle.Event.ON_PAUSE,
        "doOnStop" to Lifecycle.Event.ON_STOP,
        "doOnDestroy" to Lifecycle.Event.ON_DESTROY,
    )

    private lateinit var tabRebuildMethod: Method
    private lateinit var onTabChangedMethod: Method
    private var isSwitchOnMethod: Method? = null

    private val lifecycleMethods = mutableListOf<Pair<Method, Lifecycle.Event>>()

    private var tabFrameLayoutClass: Class<*>? = null
    private var qqTabLayoutClass: Class<*>? = null
    private var baseActivityClass: Class<*>? = null
    private var splashActivityClass: Class<*>? = null
    private var qqBlurViewWrapperClass: Class<*>? = null
    private var recyclerViewAdapterClass: Class<*>? = null
    private var quiBadgeClass: Class<*>? = null

    override fun onInit(): Boolean {
        return HookEnv.isNT() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    override fun onRun(app: Application, process: ActionProcess) {
        tabFrameLayoutClass = loadOrThrow(TAB_FRAME_LAYOUT)
        qqTabLayoutClass = loadOrThrow(QQ_TAB_LAYOUT)
        baseActivityClass = loadOrThrow(BASE_ACTIVITY)
        splashActivityClass = loadOrThrow(SPLASH_ACTIVITY)
        qqBlurViewWrapperClass = loadOrThrow(QUI_BLUR_VIEW_WRAPPER)
        recyclerViewAdapterClass = loadOrThrow(RECYCLER_VIEW_ADAPTER)
        quiBadgeClass = loadOrThrow(QUI_BADGE)

        tabRebuildMethod = tabFrameLayoutClass!!.findMethod {
            returnType = void
            paramTypes(int)
            declared
        }
        onTabChangedMethod = loadOrThrow(FRAME_FRAGMENT)
            .getDeclaredMethod("onTabChanged", String::class.java)

        lifecycleMethods.clear()
        lifecycleEvents.forEach { (methodName, event) ->
            lifecycleMethods += baseActivityClass!!.findMethod { name = methodName } to event
        }

        val unitedConfigClass = load(UNITED_CONFIG_IMPL)
        isSwitchOnMethod = unitedConfigClass?.getDeclaredMethod(
            "isSwitchOn", String::class.java, Boolean::class.javaPrimitiveType
        )

        hookUnitedConfigSwitch()
        hookLifecycle()
        hookTabRebuild()
        hookTabChanged()
        hookQuiBadge()
    }

    private fun hookUnitedConfigSwitch() {
        isSwitchOnMethod?.hookAfter { param ->
            when (param.args[0] as String) {
                "tab_layout_9065_116522266" -> param.result = true
                "tab_host_divider_switch_9.0_887617015" -> param.result = false
            }
        }
    }

    /** 把宿主 Activity 的生命周期转发给 Compose 的 [LiquidGlassLifecycleOwner]。 */
    private fun hookLifecycle() {
        val splashClass = splashActivityClass ?: return
        lifecycleMethods.forEach { (hookMethod, event) ->
            hookMethod.hookAfter { param ->
                if (!splashClass.isInstance(param.thisObject)) return@hookAfter

                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        // 新 Activity / 切换账号时先清掉上一份 Tab 模型，避免带着旧数量、旧索引，
                        // 否则重开后 Tab 会缺失（如“频道”）或出现索引错位（点到联系人却进频道）。
                        nativeTabLayout = null
                        tabTags.clear()
                        tabBadgeTexts.clear()
                        currentTabTag.value = ""
                        lifecycleOwner = LiquidGlassLifecycleOwner(param.thisObject as Activity).also {
                            it.handle(Lifecycle.Event.ON_CREATE)
                        }
                    }

                    Lifecycle.Event.ON_DESTROY -> {
                        lifecycleOwner?.handle(Lifecycle.Event.ON_DESTROY)
                        lifecycleOwner = null
                        nativeTabLayout = null
                        tabTags.clear()
                        tabBadgeTexts.clear()
                    }

                    else -> lifecycleOwner?.handle(event)
                }
            }
        }
    }

    /** 在原生 Tab 构建后隐藏之，并注入合成视图。 */
    private fun hookTabRebuild() {
        tabRebuildMethod.hookAfter { param ->
            val tabFrameLayout = param.thisObject
            val owner = lifecycleOwner ?: return@hookAfter

            val tabLayout = tabFrameLayout.getObjectByTypeOrNull(qqTabLayoutClass!!) as? ViewGroup
                ?: return@hookAfter
            nativeTabLayout = tabLayout

            hideView(tabLayout)
            val dragFrameLayout = tabLayout.parent as? ViewGroup ?: return@hookAfter
            val pageRootView = ((tabFrameLayout as? View)?.parent as? ViewGroup)?.parent as? ViewGroup
                ?: return@hookAfter

            dragFrameLayout.children
                .filter { qqBlurViewWrapperClass!!.isInstance(it) }
                .forEach { hideView(it) }

            val viewPagerAdapter = tabFrameLayout.getObjectByTypeOrNull(recyclerViewAdapterClass!!)
                ?: return@hookAfter
            val tabSpecList = viewPagerAdapter.getObjectByTypeOrNull(ArrayList::class.java) as? List<*>
                ?: return@hookAfter
            val tags = tabSpecList.mapNotNull { it?.callMethod("getTag") as? String }
            // QQ 至少保留“消息/联系人”，若读到空列表说明适配器尚未就绪，先别清空当前 Tab，
            // 以免在切换账号 / 开关“频道”的过渡期把导航栏刷成 0 个 Tab。
            if (tags.isEmpty()) return@hookAfter

            tabTags.clear()
            tabTags.addAll(tags)
            tabBadgeTexts.clear()

            currentTabTag.value = tabLayout.callMethod("getCurrentTabTag") as? String ?: ""

            ensureComposeView(dragFrameLayout, owner, pageRootView)
        }
    }

    private fun hookTabChanged() {
        onTabChangedMethod.hookAfter { param ->
            currentTabTag.value = param.args[0] as? String ?: ""
        }
    }

    private fun hookQuiBadge() {
        val badgeClass = quiBadgeClass ?: return
        badgeClass.getDeclaredMethod("setPaintColorAndValidate").hookAfter { syncBadge(it.thisObject) }
        badgeClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            .hookAfter { syncBadge(it.thisObject) }
    }

    private fun syncBadge(badge: Any) {
        val index = badge.findTabIndex() ?: return
        val view = badge as? View ?: return
        if (!view.isVisible) {
            tabBadgeTexts.remove(index)
            return
        }
        when (badge.getObjectOrNull("mViewType") as? Int ?: 0) {
            BADGE_NUM_RED -> {
                val num = badge.getObjectOrNull("mNum") as? Int ?: 0
                if (num > 0) tabBadgeTexts[index] = num.toString() else tabBadgeTexts.remove(index)
            }

            BADGE_TEXT_RED -> tabBadgeTexts[index] = badge.getObjectOrNull("mText") as? String ?: ""
            else -> tabBadgeTexts.remove(index)
        }
    }

    private fun Any.findTabIndex(): Int? {
        val badgeView = this as? View ?: return null
        val layout = nativeTabLayout ?: return null
        val tabStrip = layout.callMethod("getChildAt", 0) as? ViewGroup ?: return null
        return tabStrip.children.indexOfFirst { tabView -> badgeView.ancestors.any { it === tabView } }
            .takeIf { it >= 0 }
    }

    private fun hideView(view: View) {
        view.apply {
            isVisible = false
            if (tag != "MARKED") {
                viewTreeObserver.addOnGlobalLayoutListener {
                    if (isVisible) isVisible = false
                }
                tag = "MARKED"
            }
        }
    }

    private fun ensureComposeView(
        realParent: ViewGroup,
        owner: LiquidGlassLifecycleOwner,
        pageRootView: ViewGroup
    ) {
        val existingView = realParent.findViewWithTag(VIEW_TAG) as? ComposeView
        if (existingView != null) return

        val composeView = ComposeView(realParent.context).apply {
            tag = VIEW_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LiquidGlassTabBarContent(
                    tabTags = tabTags,
                    currentTag = currentTabTag.value,
                    badgeTexts = tabBadgeTexts,
                    pageRootView = pageRootView,
                    onTabSelected = { index, tag ->
                        currentTabTag.value = tag
                        nativeTabLayout?.callMethod("setCurrentTab", index)
                    }
                )
            }
        }

        realParent.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
    }
}
