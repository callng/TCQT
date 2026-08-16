package com.owo233.tcqt.hooks.func.activity

// 代码来自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.clazz
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.TIMVersion
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findFieldOrNull
import com.owo233.tcqt.utils.reflect.getObjectOrNull
import com.owo233.tcqt.utils.reflect.isCompatibleWith
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

@RegisterAction
class DetailedMessageCount : IAction, DexKitTask {

    override val name: String get() = "详细消息数量"
    override val desc: String get() = "显示完整未读消息数量，不再将超过上限的数量折叠为 99+。"
    override val uiTab: String get() = "界面"
    override val key: String get() = "detailed_message_count"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun onRun(app: Application, process: ActionProcess) {
        if (usesQuiBadgePath()) {
            // 群聊消息数量 + 群聊左上角返回
            hookQuiBadge(CLASS_QUI_BADGE.toClass)
        } else if (HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_63_BETA_11345)) {
            // 群聊消息数量
            hookGroupCountLegacy()
            // 群聊左上角返回
            hookLeftTopBack()
        }
        // 总消息数量
        hookTotalCount()
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_63_BETA_11345)) {
            // 小程序(菜单键)
            hookMiniProgramBadge()
            // 隐藏会话(右上角+悬浮消息列表)
            hookHiddenSession()
        }
    }

    private fun hookQuiBadge(clz: Class<*>) {
        val mNum = clz.getDeclaredField("mNum").apply { isAccessible = true }
        val mText = clz.getDeclaredField("mText").apply { isAccessible = true }
        clz.getDeclaredMethod("onDraw", Canvas::class.java).hookBefore { param ->
            val badge = param.thisObject
            val num = mNum.get(badge) as? Int ?: return@hookBefore
            if (num > 99) {
                mText.set(badge, num.toString())
            }
        }
    }

    // ── 群聊消息数量 (QQ NT 8.9.63 ~ 9.0.8) ─────────────────────────

    private fun hookGroupCountLegacy() {
        val clz = requireClass(KEY_UPDATE_CUSTOM_NOTE_TXT)
        val updateNum = clz.declaredMethods.firstOrNull { method ->
            method.matchesParams(
                TextView::class.java, Int::class.java, Int::class.java,
                Int::class.java, Int::class.java, String::class.java
            )
        } ?: return
        updateNum.hookBefore { param ->
            param.args[4] = Int.MAX_VALUE
        }
        updateNum.hookAfter { param ->
            val tv = param.args[0] as TextView
            val count = param.args[2] as Int
            val str = count.toString()
            val lp = tv.layoutParams
            lp.width = tv.context.dp((9 + 7 * str.length).toFloat())
            tv.layoutParams = lp
        }
    }

    // ── 群聊左上角返回 (QQ NT 8.9.63 ~ 9.0.8) ───────────────────────

    private fun hookLeftTopBack() {
        requireMethod(KEY_UPDATE_LEFT_TOP_BACK).hookAfter { param ->
            if (param.args[0] !is Int) return@hookAfter
            val count = param.args[0] as Int
            if (count <= 0) return@hookAfter
            val (mTitleBinding, unreadTv) = when {
                HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_0) -> Pair("e", "v")
                HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_80) -> Pair("e", "s")
                HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_70) -> Pair("e", "t")
                else -> Pair("e", "s")
            }
            val binding = param.thisObject.getObjectOrNull(mTitleBinding) ?: return@hookAfter
            val tv = binding.getObjectOrNull(unreadTv) as? TextView ?: return@hookAfter
            tv.text = "$count"
        }
    }

    // ── 总消息数量 ───────────────────────────────────────────────────

    private fun hookTotalCount() {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
            // 总消息数量 9.2.30+
            val clz = "com.tencent.mobileqq.activity.framebusiness.controllerinject.FrameControllerInjectImpl".toClass
            // 新签名 (9.3.x): b(QUIBadge, int, String, int, int)，args[4] 为显示封顶值
            val newMethod = clz.declaredMethods.firstOrNull { method ->
                method.matchesParams(
                    CLASS_QUI_BADGE.toClass, Int::class.java, String::class.java,
                    Int::class.java, Int::class.java
                )
            }
            if (newMethod != null) {
                newMethod.hookBefore { param ->
                    param.args[4] = Int.MAX_VALUE
                }
                return
            }
            // 旧签名 (9.2.30 ~ 9.2.55): (int, int, int, QUIBadge, String)，args[2] 为显示封顶值
            val oldMethod = clz.declaredMethods.firstOrNull { method ->
                method.matchesParams(
                    Int::class.java, Int::class.java, Int::class.java,
                    CLASS_QUI_BADGE.toClass, String::class.java
                )
            } ?: return
            oldMethod.hookBefore { param ->
                param.args[2] = Int.MAX_VALUE
            }
        } else if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_8) ||
            HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)) {
            // 总消息数量 9.0.8 ~ 9.2.20
            val clz = customWidgetUtilClass()
            val method = clz.declaredMethods.firstOrNull { method ->
                method.matchesParams(
                    CLASS_QUI_BADGE.toClass, Int::class.java, Int::class.java,
                    Int::class.java, String::class.java
                )
            } ?: return
            method.hookBefore { param ->
                param.args[3] = Int.MAX_VALUE
            }
        } else {
            // 总消息数量(QQ[9.0.8]之前) + 群消息数量(QQNT[8.9.63]之前)
            val clz = customWidgetUtilClass()
            val method = clz.declaredMethods.firstOrNull { method ->
                if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_0)) {
                    method.matchesParams(
                        TextView::class.java, Int::class.java, Int::class.java,
                        Int::class.java, Int::class.java, String::class.java, Boolean::class.java
                    )
                } else {
                    method.matchesParams(
                        TextView::class.java, Int::class.java, Int::class.java,
                        Int::class.java, Int::class.java, String::class.java
                    )
                }
            } ?: return
            method.hookBefore { param ->
                param.args[4] = Int.MAX_VALUE
            }
            method.hookAfter { param ->
                (param.args[0] as TextView).apply {
                    maxWidth = Int.MAX_VALUE
                    layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    setPadding(0, 0, 0, 0)
                }
            }
        }
    }

    private fun customWidgetUtilClass(): Class<*> {
        return CLASS_CUSTOM_WIDGET_UTIL.clazz ?: requireClass(KEY_CUSTOM_WIDGET_UTIL)
    }

    // ── 小程序(菜单键) ───────────────────────────────────────────────

    private fun hookMiniProgramBadge() {
        "com.tencent.qqmini.sdk.core.utils.CustomWidgetUtil".toClass
            .getDeclaredMethod("updateCustomNoteTxt", TextView::class.java, Int::class.java)
            .hookAfter { param ->
                (param.args[0] as TextView).text = "${param.args[1] as Int}"
            }
    }

    // ── 隐藏会话(右上角+悬浮消息列表) ────────────────────────────────

    private fun hookHiddenSession() {
        val (floatViewManagerClass, msgUnreadCallbackClass) = when {
            HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30) -> Pair(// 9.2.30
                "com.tencent.mobileqq.activity.miniaio.c", "com.tencent.mobileqq.activity.miniaio.d"
            )

            HookEnv.requireMinQQVersion(QQVersion.QQ_9_1_50) -> Pair(// 9.1.50 ~ 9.2.10
                "com.tencent.mobileqq.activity.miniaio.e", "com.tencent.mobileqq.activity.miniaio.d"
            )

            HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_90) -> Pair(// 9.0.90 ~ 9.1.30
                "com.tencent.mobileqq.activity.miniaio.i", "com.tencent.mobileqq.activity.miniaio.h"
            )

            HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_60) -> Pair(// 9.0.60 ~ 9.0.70
                "com.tencent.mobileqq.activity.miniaio.h", "com.tencent.mobileqq.activity.miniaio.g"
            )

            HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_55) -> Pair(// 9.0.55 ~ 9.0.56
                "com.tencent.mobileqq.activity.miniaio.f", "com.tencent.mobileqq.activity.miniaio.e"
            )

            else -> Pair(// 8.9.70 ~ 9.0.50
                "com.tencent.mobileqq.activity.miniaio.i", "com.tencent.mobileqq.activity.miniaio.h"
            )
        }
        // 隐藏会话右上角
        floatViewManagerClass.clazz?.declaredMethods
            ?.firstOrNull { it.name == "updateUnreadCount" && it.parameterTypes.size == 2 }
            ?.hookAfter { param ->
                val rootView = param.thisObject.javaClass.findFieldOrNull { type = View::class.java }
                    ?.get(param.thisObject) as? ViewGroup ?: return@hookAfter
                (rootView.findViewByType(TextView::class.java) as? TextView)?.text =
                    "${param.args[0] as Int}"
            }
        // 隐藏会话悬浮消息列表
        msgUnreadCallbackClass.clazz?.declaredMethods
            ?.firstOrNull { it.name == "updateUnreadCount" && it.parameterTypes.size == 2 }
            ?.hookAfter { param ->
                val tv = param.thisObject.javaClass.findFieldOrNull { type = TextView::class.java }
                    ?.get(param.thisObject) as? TextView ?: return@hookAfter
                tv.text = "${param.args[0] as Int}"
            }
    }

    // ── DexKit 查询 ──────────────────────────────────────────────────

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        // 群聊消息数量 (QQ NT 8.9.63 ~ 9.0.8): com.tencent.qqnt.chats 包内的 CustomWidgetUtil
        KEY_UPDATE_CUSTOM_NOTE_TXT to FindClass().apply {
            searchPackages("com.tencent.qqnt.chats")
            matcher {
                usingStrings("fixTextViewLayout wrong: params wrong")
                methods {
                    add {
                        paramTypes(
                            "android.widget.TextView", "int", "int", "int", "int", "java.lang.String"
                        )
                    }
                    add {
                        paramTypes(
                            "android.widget.TextView", "java.lang.Integer", "java.lang.Integer",
                            "java.lang.Integer", "java.lang.Integer", "java.lang.String"
                        )
                    }
                }
            }
        },
        // 群聊左上角返回 (QQ NT 8.9.63 ~ 9.0.8): AIOTitleVB.updateLeftTopBack
        KEY_UPDATE_LEFT_TOP_BACK to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.title")
            matcher {
                usingStrings("99+")
            }
        },
        // 总消息数量: com.tencent.widget.CustomWidgetUtil
        KEY_CUSTOM_WIDGET_UTIL to FindClass().apply {
            searchPackages("com.tencent.widget")
            matcher {
                usingStrings(listOf("^NEW$"), StringMatchType.SimilarRegex)
                methods {
                    // 9.0.8+: updateCustomNoteTxt(QUIBadge, int, int, int, String)
                    add {
                        paramTypes(
                            "com.tencent.mobileqq.quibadge.QUIBadge",
                            "int", "int", "int", "java.lang.String"
                        )
                    }
                    add {
                        paramTypes(
                            "com.tencent.mobileqq.quibadge.QUIBadge",
                            "java.lang.Integer", "java.lang.Integer",
                            "java.lang.Integer", "java.lang.String"
                        )
                    }
                    // 9.0.0 ~ 9.0.7: updateCustomNoteTxt(TextView, int, int, int, int, String, boolean)
                    add {
                        paramTypes(
                            "android.widget.TextView", "int", "int", "int", "int",
                            "java.lang.String", "boolean"
                        )
                    }
                    add {
                        paramTypes(
                            "android.widget.TextView", "java.lang.Integer", "java.lang.Integer",
                            "java.lang.Integer", "java.lang.Integer",
                            "java.lang.String", "java.lang.Boolean"
                        )
                    }
                    // < 9.0.0: updateCustomNoteTxt(TextView, int, int, int, int, String)
                    add {
                        paramTypes(
                            "android.widget.TextView", "int", "int", "int", "int", "java.lang.String"
                        )
                    }
                    add {
                        paramTypes(
                            "android.widget.TextView", "java.lang.Integer", "java.lang.Integer",
                            "java.lang.Integer", "java.lang.Integer", "java.lang.String"
                        )
                    }
                }
            }
        }
    )

    override fun getCacheKeys(): Set<String> = buildSet {
        // 群聊消息数量/左上角返回 (QQ NT 8.9.63 ~ 9.0.8)
        if (!usesQuiBadgePath() &&
            HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_63_BETA_11345)
        ) {
            add(KEY_UPDATE_CUSTOM_NOTE_TXT)
            add(KEY_UPDATE_LEFT_TOP_BACK)
        }
        // 总消息数量 (QQ < 9.2.30 或 TIM NT 4.0.95+)
        if (!HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30) ||
            HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)
        ) {
            add(KEY_CUSTOM_WIDGET_UTIL)
        }
    }

    private fun usesQuiBadgePath(): Boolean {
        return HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_8) ||
            HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)
    }

    private companion object {
        const val KEY_UPDATE_CUSTOM_NOTE_TXT = "updateCustomNoteTxtNt"
        const val KEY_UPDATE_LEFT_TOP_BACK = "updateLeftTopBack"
        const val KEY_CUSTOM_WIDGET_UTIL = "customWidgetUtil"

        const val CLASS_CUSTOM_WIDGET_UTIL = "com.tencent.widget.CustomWidgetUtil"
        const val CLASS_QUI_BADGE = "com.tencent.mobileqq.quibadge.QUIBadge"
    }
}

private fun Method.matchesParams(vararg expected: Class<*>): Boolean {
    val params = parameterTypes
    if (params.size != expected.size) return false
    return params.indices.all { i -> params[i].isCompatibleWith(expected[i]) }
}

private fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

private fun ViewGroup.findViewByType(type: Class<*>): View? {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (type.isInstance(child)) return child
        if (child is ViewGroup) {
            child.findViewByType(type)?.let { return it }
        }
    }
    return null
}
