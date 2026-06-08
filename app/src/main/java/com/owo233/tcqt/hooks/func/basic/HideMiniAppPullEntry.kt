package com.owo233.tcqt.hooks.func.basic


import android.app.Application
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.invoke
import java.lang.reflect.Modifier

@RegisterAction
class HideMiniAppPullEntry : IAction {

    override val name: String get() = "隐藏下拉小程序"
    override val desc: String get() = "生成屏蔽下拉小程序解决方案。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "hide_mini_app_pull_entry"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isTim()) return
        val rule = currentHookRule() ?: run {
            Log.e("隐藏下拉小程序: 当前 QQ 版本 ${HookEnv.versionCode} 未配置 Hook 规则")
            return
        }

        when (rule.hookPoint) {
            HookPoint.OLD_STYLE_HEADER -> hookOldStyleHeader(rule)
            HookPoint.MINI_APP_REFRESH_PART -> hookMiniAppRefreshPart(rule)
        }
    }

    private fun hookOldStyleHeader(rule: HookRule) {
        hookMiniAppRefreshPartAlphaCallbacks()
        hookHeaderBaseVisibilityCallbacks()
        if (!hookMiniOldStyleHeaderNew(rule)) {
            hookMiniOldStyleHeader(rule)
        }
    }

    private fun hookMiniOldStyleHeaderNew(rule: HookRule): Boolean {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew") ?: return false
        hookHeaderClass(clazz, rule)
        return true
    }

    private fun hookMiniOldStyleHeader(rule: HookRule) {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeader") ?: return
        hookHeaderClass(clazz, rule)
    }

    private fun hookMiniAppRefreshPart(rule: HookRule) {
        hookMiniAppRefreshPartAlphaCallbacks()
        hookHeaderBaseVisibilityCallbacks()
        MINI_APP_HEADER_CLASS_NAMES
            .mapNotNull { load(it) }
            .forEach { hookHeaderClass(it, rule) }

        val refreshPart = load(MINI_APP_REFRESH_PART) ?: run {
            Log.e("隐藏下拉小程序: MiniAppRefreshPart 未找到，回退旧版 Header Hook")
            fallbackOldStyleRule()?.let { hookOldStyleHeader(it) }
                ?: Log.e(
                    "隐藏下拉小程序: 当前 QQ 版本 ${HookEnv.versionCode} 未配置旧版 Header 回退规则"
                )
            return
        }

        refreshPart.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.isTwoLevelHeaderClass()
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { param ->
                    val header = param.result ?: return@hookAfter
                    disableTwoLevelHeader(header, rule)
                    replaceMiniAppContainerWithRefreshHeader(header)
                }
            }
    }

    private fun hookMiniAppRefreshPartAlphaCallbacks() {
        val refreshPart = load(MINI_APP_REFRESH_PART) ?: return
        if (!hookedMiniAppRefreshPartAlphaCallbackClasses.add(refreshPart.name)) return
        refreshPart.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == java.lang.Float.TYPE &&
                    method.parameterTypes[2] == java.lang.Float.TYPE &&
                    method.parameterTypes[3] == java.lang.Float.TYPE
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { param ->
                    val view = param.args.firstOrNull() as? View ?: return@hookAfter
                    keepMiniAppPullAlphaViewVisible(view)
                    view.post { keepMiniAppPullAlphaViewVisible(view) }
                }
            }
    }

    private fun hookHeaderClass(clazz: Class<*>, rule: HookRule) {
        hookHeaderConstructors(clazz, rule)
        hookHeaderVisibilityCallbacks(clazz)
    }

    private fun hookHeaderConstructors(clazz: Class<*>, rule: HookRule) {
        clazz.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            ctor.hookAfter { param ->
                disableTwoLevelHeader(param.thisObject, rule)
                replaceMiniAppContainerWithRefreshHeader(param.thisObject)
            }
        }
    }

    private fun hookHeaderBaseVisibilityCallbacks() {
        MINI_APP_HEADER_BASE_CLASS_NAMES
            .mapNotNull { load(it) }
            .forEach { hookHeaderVisibilityCallbacks(it) }
    }

    private fun hookHeaderVisibilityCallbacks(clazz: Class<*>) {
        if (!hookedHeaderVisibilityCallbackClasses.add(clazz.name)) return
        clazz.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.isHeaderVisibilityCallback()
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { param ->
                    val header = param.thisObject
                    if (header.isMiniAppHeaderInstance()) {
                        replaceMiniAppContainerWithRefreshHeader(header)
                        (header as? View)?.post {
                            replaceMiniAppContainerWithRefreshHeader(header)
                        }
                    }
                }
            }
    }

    private fun java.lang.reflect.Method.isHeaderVisibilityCallback(): Boolean {
        return isRefreshStateCallback() || isHeaderMovingCallback()
    }

    private fun java.lang.reflect.Method.isRefreshStateCallback(): Boolean {
        return parameterTypes.size == 3 &&
            parameterTypes[1].name == REFRESH_STATE_CLASS_NAME &&
            parameterTypes[2].name == REFRESH_STATE_CLASS_NAME
    }

    private fun java.lang.reflect.Method.isHeaderMovingCallback(): Boolean {
        return parameterTypes.isHeaderMovingCallbackTypes(0) ||
            parameterTypes.isHeaderMovingCallbackTypes(1)
    }

    private fun Array<Class<*>>.isHeaderMovingCallbackTypes(offset: Int): Boolean {
        return size == offset + 5 &&
            this[offset] == java.lang.Boolean.TYPE &&
            this[offset + 1] == java.lang.Float.TYPE &&
            this[offset + 2] == java.lang.Integer.TYPE &&
            this[offset + 3] == java.lang.Integer.TYPE &&
            this[offset + 4] == java.lang.Integer.TYPE
    }

    private fun disableTwoLevelHeader(header: Any, rule: HookRule) {
        val fieldName = rule.enableTwoLevelField ?: return
        runCatching {
            val targetClass = rule.fieldOwnerClassName
                ?.let { owner ->
                    collectClassHierarchy(header.javaClass)
                        .firstOrNull { it.name == owner }
                }
                ?: header.javaClass.superclass?.superclass?.superclass
                ?: return
            FieldUtils.create(header)
                .named(fieldName)
                .inParent(targetClass)
                .setValue(false)
        }
    }

    private fun replaceMiniAppContainerWithRefreshHeader(header: Any) {
        hideMiniAppContainer(header)
        showWrappedRefreshHeader(header)
    }

    private fun hideMiniAppContainer(header: Any) {
        getMiniAppContainer(header)
            ?.setViewVisibility(View.GONE)
    }

    private fun getMiniAppContainer(header: Any): Any? {
        return runCatching {
            header.invoke("s")
        }.recoverCatching {
            header.invoke("o")
        }.getOrNull()
    }

    private fun showWrappedRefreshHeader(header: Any) {
        val miniAppContainer = getMiniAppContainer(header) as? View
        collectClassHierarchy(header.javaClass)
            .firstOrNull { it.name == SMART_REFRESH_TWO_LEVEL_HEADER }
            ?.declaredFields
            ?.filter { field -> !Modifier.isStatic(field.modifiers) }
            ?.forEach { field ->
                val view = runCatching {
                    field.isAccessible = true
                    field.get(header)?.invoke("getView") as? View
                }.getOrNull() ?: return@forEach
                if (view !== miniAppContainer) {
                    keepRefreshVisualViewVisible(view)
                }
            }
    }

    private fun keepRefreshVisualViewVisible(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1.0f
        view.background?.alpha = 255
    }

    private fun keepMiniAppPullAlphaViewVisible(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1.0f
    }

    private fun Any.setViewVisibility(visibility: Int) {
        val androidView = this as? View
        if (androidView != null) {
            androidView.visibility = visibility
        } else {
            runCatching { invoke("setVisibility", visibility) }
        }
    }

    private fun Any.isMiniAppHeaderInstance(): Boolean {
        val classNames = collectClassHierarchy(javaClass).map { it.name }
        return MINI_APP_HEADER_CLASS_NAMES.any { it in classNames }
    }

    private fun currentHookRule(): HookRule? {
        return HOOK_RULES.firstOrNull { it.matchesCurrentVersion() }
    }

    private fun fallbackOldStyleRule(): HookRule? {
        return HOOK_RULES.firstOrNull { rule ->
            rule.hookPoint == HookPoint.OLD_STYLE_HEADER && rule.matchesCurrentVersion()
        }
    }

    private fun HookRule.matchesCurrentVersion(): Boolean {
        if (!HookEnv.isQQ()) return false
        val versionCode = HookEnv.versionCode
        return (minVersion == null || versionCode >= minVersion) &&
            (maxVersionExclusive == null || versionCode < maxVersionExclusive)
    }

    private fun collectClassHierarchy(clazz: Class<*>): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            classes.add(current)
            current = current.superclass
        }
        return classes
    }

    private fun Class<*>.isTwoLevelHeaderClass(): Boolean {
        return collectClassHierarchy(this)
            .any { it.name == SMART_REFRESH_TWO_LEVEL_HEADER }
    }

    private enum class HookPoint {
        OLD_STYLE_HEADER,
        MINI_APP_REFRESH_PART
    }

    private data class HookRule(
        val minVersion: Long?,
        val hookPoint: HookPoint,
        val enableTwoLevelField: String?,
        val maxVersionExclusive: Long? = null,
        val fieldOwnerClassName: String? = null
    )

    companion object {
        private const val MINI_APP_REFRESH_PART = "com.tencent.mobileqq.activity.home.chats.biz.MiniAppRefreshPart"
        private const val SMART_REFRESH_TWO_LEVEL_HEADER = "com.qqnt.widget.smartrefreshlayout.header.TwoLevelHeader"
        private const val REFRESH_STATE_CLASS_NAME = "com.qqnt.widget.smartrefreshlayout.layout.constant.RefreshState"

        // 版本适配维护区：按 [minVersion, maxVersionExclusive) 区间限制 Hook 点与字段名。
        //
        // 维护方式：
        // 1. 不要 Hook/替换 Conversation 的小程序初始化方法。该初始化同时绑定原生下拉刷新 listener，
        //    替换整段会导致刷新可见但无法真正触发或结束。
        // 2. 先在反编译结果中定位 com.tencent.mobileqq.activity.home.chats.biz.MiniAppRefreshPart。
        //    如果该类存在，并且存在无参方法返回 com.qqnt.widget.smartrefreshlayout.header.TwoLevelHeader
        //    或其子类，优先使用 HookPoint.MINI_APP_REFRESH_PART。
        // 3. 再定位 com.qqnt.widget.smartrefreshlayout.header.TwoLevelHeader，确认“允许进入二级刷新”
        //    的布尔字段名。判断方法：看 onMoving/状态切换逻辑中用于进入 ReleaseToTwoLevel 的 boolean。
        //    当前已知：9.3.0 Beta 36720 为 t；9.1.70 ~ 9.2.95 为 I；更旧版本见下方规则。
        // 4. 如果新版 MiniAppRefreshPart 结构消失，回退到 HookPoint.OLD_STYLE_HEADER，并从
        //    MiniOldStyleHeaderNew / MiniOldStyleHeader 的父类链中确认 TwoLevelHeader 字段名。
        // 5. 新增版本时优先新增一个窄范围规则，测试刷新开始、刷新结束、小程序页隐藏都正常后，
        //    再考虑扩大 maxVersionExclusive 或移除上界。
        //
        // 本 Hook 的目标只是在 MiniApp Header 层禁用二级下拉并替换显示层，不接管刷新状态机。
        private val HOOK_RULES = listOf(
            HookRule(
                minVersion = QQVersion.QQ_9_3_0_BETA_36720,
                hookPoint = HookPoint.MINI_APP_REFRESH_PART,
                enableTwoLevelField = "t",
                fieldOwnerClassName = SMART_REFRESH_TWO_LEVEL_HEADER
            ),
            HookRule(
                minVersion = QQVersion.QQ_9_1_70,
                maxVersionExclusive = QQVersion.QQ_9_3_0_BETA_36720,
                hookPoint = HookPoint.OLD_STYLE_HEADER,
                enableTwoLevelField = "I"
            ),
            HookRule(
                minVersion = QQVersion.QQ_9_1_30,
                maxVersionExclusive = QQVersion.QQ_9_1_70,
                hookPoint = HookPoint.OLD_STYLE_HEADER,
                enableTwoLevelField = "E"
            ),
            HookRule(
                minVersion = null,
                maxVersionExclusive = QQVersion.QQ_9_1_30,
                hookPoint = HookPoint.OLD_STYLE_HEADER,
                enableTwoLevelField = "D"
            )
        )

        private val MINI_APP_HEADER_CLASS_NAMES = listOf(
            "com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew",
            "com.tencent.qqnt.chats.view.MiniOldStyleHeader",
            "com.tencent.qqnt.chats.view.QQMiniProgramHeader",
            "com.tencent.qqnt.chats.view.MiniProgramHeader"
        )

        private val MINI_APP_HEADER_BASE_CLASS_NAMES = listOf(
            "com.tencent.qqnt.chats.view.QQChatListTwoLevelHeader",
            "com.tencent.qqnt.chats.view.ChatListTwoLevelHeader"
        )

        private val hookedHeaderVisibilityCallbackClasses = mutableSetOf<String>()
        private val hookedMiniAppRefreshPartAlphaCallbackClasses = mutableSetOf<String>()
    }
}
