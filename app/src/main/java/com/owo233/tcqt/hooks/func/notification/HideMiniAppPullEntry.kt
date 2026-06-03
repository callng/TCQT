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

@RegisterAction
class HideMiniAppPullEntry : IAction, DexKitTask {

    override val name: String get() = "隐藏下拉小程序"
    override val desc: String get() = "生成屏蔽下拉小程序解决方案。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "hide_mini_app_pull_entry"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

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
    }

    private fun hookMiniOldStyleHeaderNew(): Boolean {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew") ?: return false
        clazz.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            ctor.hookAfter { param ->
                disableTwoLevelHeader(param.thisObject)
            }
        }

        runCatching { requireMethod(HEADER_NEW_STATE_CHANGED) }.getOrNull()
            ?.hookAfter { param ->
                finishRefresh(param.args.getOrNull(0))
            }
            ?: Log.e("隐藏下拉小程序: MiniOldStyleHeaderNew 刷新收回 Hook 未定位")
        return true
    }

    private fun hookMiniOldStyleHeader() {
        val clazz = load("com.tencent.qqnt.chats.view.MiniOldStyleHeader") ?: return
        clazz.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            ctor.hookAfter { param ->
                disableTwoLevelHeader(param.thisObject)
            }
        }

        clazz.findMethodOrNull {
            name = "a"
            paramCount = 3
        }?.hookAfter { param ->
            finishRefresh(param.args.getOrNull(0))
        }
    }

    private fun finishRefresh(refreshLayout: Any?) {
        if (refreshLayout == null) return
        runCatching {
            refreshLayout.invoke("finishRefresh")
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
