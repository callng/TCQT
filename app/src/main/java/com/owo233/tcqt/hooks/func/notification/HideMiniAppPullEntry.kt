package com.owo233.tcqt.hooks.func.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.findMethodOrNull
import com.owo233.tcqt.utils.reflect.invoke
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

@RegisterAction
class HideMiniAppPullEntry : IAction, DexKitTask {

    override val name: String get() = "隐藏下拉小程序"
    override val desc: String get() = "生成屏蔽下拉小程序解决方案。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "hide_mini_app_pull_entry"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    private val hookedHeaderClassNames = HashSet<String>()

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isTim()) return

        runCatching {
            requireMethod(CONVERSATION_INIT_MINI_APP).hookReplace { null }
        }.onFailure {
            Log.e("隐藏下拉小程序: Conversation 小程序入口 Hook 失效", it)
        }
        if (!hookMiniOldStyleHeaderNew()) {
            hookMiniOldStyleHeader()
        }
        hookChatListTwoLevelHeader()
        hookMiniAppRefreshPart()
    }

    private fun hookMiniOldStyleHeaderNew(): Boolean {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew") ?: return false
        hookHeaderConstructors(clazz)

        runCatching { requireMethod(HEADER_NEW_STATE_CHANGED) }.getOrNull()
            ?.hookAfter { param ->
                finishRefresh(param.args.getOrNull(0))
            }
            ?: Log.e("隐藏下拉小程序: MiniOldStyleHeaderNew 刷新收回 Hook 未定位")
        return true
    }

    private fun hookMiniOldStyleHeader() {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeader") ?: return
        hookHeaderConstructors(clazz)

        clazz.findMethodOrNull {
            name = "a"
            paramCount = 3
        }?.hookAfter { param ->
            finishRefresh(param.args.getOrNull(0))
        }
    }

    private fun hookChatListTwoLevelHeader(): Boolean {
        val clazz = listOf(
            "com.tencent.qqnt.chats.view.QQChatListTwoLevelHeader",
            "com.tencent.qqnt.chats.view.ChatListTwoLevelHeader"
        ).firstNotNullOfOrNull { load(it) } ?: return false

        hookHeaderConstructors(clazz)
        hookDynamicHeaderClass(clazz)
        return true
    }

    private fun hookMiniAppRefreshPart() {
        val clazz = load("com.tencent.mobileqq.activity.home.chats.biz.MiniAppRefreshPart") ?: return

        clazz.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.isTwoLevelHeaderClass()
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { param ->
                    val header = param.result ?: return@hookAfter
                    hookDynamicHeaderClass(header.javaClass)
                    disableTwoLevelHeader(header)
                    hideHeaderContainer(header)
                }
            }
    }

    private fun hookHeaderConstructors(clazz: Class<*>) {
        clazz.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            ctor.hookAfter { param ->
                val header = param.thisObject ?: return@hookAfter
                disableTwoLevelHeader(header)
                hideHeaderContainer(header)
            }
        }
    }

    private fun hookHeaderStateChanged(clazz: Class<*>) {
        collectHeaderClasses(clazz)
            .flatMap { it.declaredMethods.asIterable() }
            .filter { it.isRefreshStateChangedMethod() }
            .distinctBy { it.toGenericString() }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { param ->
                    finishRefresh(param.args.getOrNull(0))
                }
            }
    }

    private fun hookDynamicHeaderClass(clazz: Class<*>) {
        if (!hookedHeaderClassNames.add(clazz.name)) return
        hookHeaderStateChanged(clazz)
    }

    private fun finishRefresh(refreshLayout: Any?) {
        if (refreshLayout == null) return
        val success = runCatching {
            refreshLayout.invoke("finishRefresh")
        }.isSuccess
        if (success) return
        runCatching {
            refreshLayout.invoke("finishTwoLevel")
        }.onFailure {
            Log.e("隐藏下拉小程序: finishRefresh 调用失败", it)
        }
    }

    private fun disableTwoLevelHeader(header: Any) {
        runCatching {
            val targetClass = header.javaClass.superclass?.superclass?.superclass ?: return
            val fieldName = when {
                HookEnv.requireMinQQVersion(QQVersion.QQ_9_1_70) -> "I"
                HookEnv.requireMinQQVersion(QQVersion.QQ_9_1_30) -> "E"
                else -> "D"
            }
            FieldUtils.create(header)
                .named(fieldName)
                .inParent(targetClass)
                .setValue(false)
        }
        disableTwoLevelHeaderByClassShape(header)
    }

    private fun disableTwoLevelHeaderByClassShape(header: Any) {
        collectHeaderClasses(header.javaClass)
            .filter { clazz -> clazz.name.contains("TwoLevelHeader", ignoreCase = true) }
            .flatMap { it.declaredFields.asIterable() }
            .filter { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type == Boolean::class.javaPrimitiveType
            }
            .forEach { field ->
                runCatching {
                    field.isAccessible = true
                    field.setBoolean(header, false)
                }
            }
    }

    private fun hideHeaderContainer(header: Any) {
        runCatching {
            header.invoke("s")
        }.recoverCatching {
            header.invoke("o")
        }.getOrNull()
            ?.let { view ->
                runCatching { view.invoke("setVisibility", 8) }
                runCatching { view.invoke("removeAllViews") }
            }
    }

    private fun collectHeaderClasses(clazz: Class<*>): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            classes.add(current)
            current = current.superclass
        }
        return classes
    }

    private fun Method.isRefreshStateChangedMethod(): Boolean {
        if (Modifier.isStatic(modifiers) || parameterTypes.size != 3) return false
        return parameterTypes[1].name.contains("RefreshState") &&
            parameterTypes[2].name.contains("RefreshState")
    }

    private fun Class<*>.isTwoLevelHeaderClass(): Boolean {
        return collectHeaderClasses(this)
            .any { it.name.contains("TwoLevelHeader", ignoreCase = true) }
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        val conversationTraits = listOf(
            "initMiniAppEntryLayout.",
            "initMicroAppEntryLayout.",
            "init Mini App, cost="
        )
        conversationTraits.asSequence()
            .mapNotNull { trait -> bridge.findMethod(conversationQueryByString(trait)).firstOrNull() }
            .firstOrNull()
            ?.let { cache[CONVERSATION_INIT_MINI_APP] = it.descriptor }
            ?: bridge.findMethod(conversationQueryByInvoke())
                .firstOrNull()
                ?.let { cache[CONVERSATION_INIT_MINI_APP] = it.descriptor }

        bridge.findMethod(
            FindMethod().apply {
                matcher {
                    declaredClass("com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew")
                    paramCount = 3
                    usingStrings("refreshLayout", "oldState", "newState")
                }
            }
        ).firstOrNull()?.let { cache[HEADER_NEW_STATE_CHANGED] = it.descriptor }
    }

    private fun conversationQueryByString(trait: String): FindMethod {
        return FindMethod().apply {
            matcher {
                declaredClass("com.tencent.mobileqq.activity.home.Conversation")
                usingStrings(trait)
            }
        }
    }

    private fun conversationQueryByInvoke(): FindMethod {
        return FindMethod().apply {
            matcher {
                declaredClass("com.tencent.mobileqq.activity.home.Conversation")
                addInvoke {
                    declaredClass = "com.tencent.mobileqq.mini.api.IMiniAppService"
                    name = "createMiniAppEntryManager"
                }
            }
        }
    }

    companion object {
        private const val CONVERSATION_INIT_MINI_APP = "HideMiniAppPullEntry.conversationMiniAppInit"
        private const val HEADER_NEW_STATE_CHANGED = "HideMiniAppPullEntry.headerStateChanged"
    }
}
