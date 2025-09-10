package com.owo233.tcqt.hooks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.XpClassLoader.hostClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.fieldValue
import com.owo233.tcqt.utils.invoke
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.utils.new
import java.lang.reflect.Proxy

@RegisterAction
class AddModuleEntrance : AlwaysRunAction() {
    override fun onRun(ctx: Context, process: ActionProcess) {
        val cMainSettingFragment =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingFragment")
        cMainSettingFragment ?: return

        val oldEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider")
        val newEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider")

        createEntry(oldEntry, false)
        createEntry(newEntry, true)
    }

    @SuppressLint("DiscouragedApi")
    private fun createEntry(settingConfigProviderClass: Class<*>?, isNewSetting: Boolean) {
        settingConfigProviderClass ?: return

        runCatching {
            val methods = settingConfigProviderClass.getMethods(false)

            // 找到返回 processor 的方法
            val simpleItemProcessorClass = methods.firstOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType.name.startsWith("com.tencent.mobileqq.setting.processor")
            }?.returnType ?: return@runCatching logE(msg = "未找到 SimpleItemProcessor 方法")

            // 找到构造函数参数数量
            val processorArgCount = simpleItemProcessorClass.constructors.firstOrNull {
                it.parameterTypes.size < 6 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.parameterTypes[1] == Int::class.java &&
                        it.parameterTypes[2] == CharSequence::class.java &&
                        it.parameterTypes[3] == Int::class.java &&
                        (it.parameterTypes.size < 5 || it.parameterTypes[4] == String::class.java)
            }?.parameterTypes?.size ?: return@runCatching logE(msg = "未找到 SimpleItemProcessor 构造函数")

            // 找到 build 方法
            val buildMethod = methods.singleOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType == List::class.java
            } ?: return@runCatching logE(msg = "未找到 build 方法")

            // 找到点击监听器
            val listeners = simpleItemProcessorClass.getMethods(false).filter {
                it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == "kotlin.jvm.functions.Function0"
            }.sortedBy { it.name }

            if (listeners.size > 2) {
                throw IllegalStateException("listeners.size > 2, count=${listeners.size}")
            }

            val onClickMethod = listeners.first()

            // Hook 逻辑
            val hooker = afterHook { param ->
                val context = param.args.firstOrNull() as? Context ?: return@afterHook
                val result = param.result as? MutableList<*> ?: return@afterHook

                val resId = context.resources.getIdentifier(
                    "qui_setting", "drawable", hostInfo.packageName
                )

                // 创建 SimpleItemProcessor 实例
                val item = simpleItemProcessorClass.new(
                    *listOf(
                        context,
                        R.id.setting2Activity_settingEntryItem,
                        "TCQT",
                        resId,
                        ""
                    ).take(processorArgCount).toTypedArray()
                )

                // 创建点击事件代理
                val function0Class = onClickMethod.parameterTypes.first()
                val unit = hostClassLoader.loadClass("kotlin.Unit")?.fieldValue("INSTANCE") ?: Unit
                val proxy = Proxy.newProxyInstance(hostClassLoader, arrayOf(function0Class)) { _, method, _ ->
                    if (method.name == "invoke") {
                        runCatching {
                            val browser = XpClassLoader.load("com.tencent.mobileqq.activity.QQBrowserDelegationActivity")
                                ?: return@runCatching logE(msg = "无法加载浏览器类")

                            val intent = Intent(context, browser).apply {
                                putExtra("fling_action_key", 2)
                                putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                                putExtra("url", "http://${TCQTSetting.settingUrl}")
                                putExtra("hide_more_button", true)
                                putExtra("hide_operation_bar",true)
                                putExtra("hide_title_bar", true)
                                putExtra("hide_title_left_arrow", true)
                                putExtra("hide_left_button", true)
                                putExtra("hideRightButton", true)
                                putExtra("finish_animation_up_down", true)
                                putExtra("ishiderefresh", true)
                                putExtra("ishidebackforward", true)
                                putExtra("portraitOnly", true)
                                putExtra("webStyle", "noBottomBar")
                            }
                            context.startActivity(intent)
                        }.onFailure { logE(msg = "进入模块设置入口失败", cause = it) }
                    }
                    unit
                }

                onClickMethod.invoke(item, proxy)

                // 创建 Group 并插入
                val groupClass = result.firstOrNull()?.javaClass ?: return@afterHook
                val groupConstructor = groupClass.getConstructor(
                    List::class.java,
                    CharSequence::class.java,
                    CharSequence::class.java,
                    Int::class.javaPrimitiveType,
                    hostClassLoader.loadClass("kotlin.jvm.internal.DefaultConstructorMarker")
                )

                val group = groupConstructor.newInstance(listOf(item), null, null, 6, null)
                val insertIndex = if (isNewSetting) 1 else 0
                result.invoke("add", insertIndex, group)
            }

            buildMethod.hookMethod(hooker)

        }.onFailure { e ->
            logE(msg = "创建模块设置入口失败", cause = e)
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
