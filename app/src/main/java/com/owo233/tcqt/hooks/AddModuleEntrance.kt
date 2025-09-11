package com.owo233.tcqt.hooks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.fieldValue
import com.owo233.tcqt.utils.invoke
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.utils.new
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RegisterAction
class AddModuleEntrance : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingFragment")
            ?: logE(msg = "找不到MainSettingFragment类,无法创建模块入口!!!")

        val oldEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider")
        val newEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider")

        if (oldEntry == null && newEntry == null) {
            logE(msg = "找不到SettingConfigProvider,无法创建模块入口!!!")
        }

        // 创建入口
        createEntry(oldEntry ?: newEntry, oldEntry == null)
    }

    /**
     * 在设置页插入一个自定义入口
     *
     * @param settingConfigProviderClass SettingConfigProvider 类
     * @param isNewSetting 是否为新版设置页
     */
    @SuppressLint("DiscouragedApi")
    private fun createEntry(settingConfigProviderClass: Class<*>?, isNewSetting: Boolean) {
        settingConfigProviderClass ?: return

        runCatching {
            val methods = settingConfigProviderClass.getMethods(false)

            // 1. 查找返回类型为 processor.* 的第一个方法，获取其返回值类型
            val processorClass = methods.firstOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType.name.startsWith("com.tencent.mobileqq.setting.processor")
            }?.returnType ?: return@runCatching logE(msg = "无法找到返回 processor.* 类")

            // 2. 找到合适的构造函数, 用于创建item
            val processorArgCount = processorClass.constructors.firstOrNull {
                it.parameterTypes.size < 6 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        it.parameterTypes[2] == CharSequence::class.java &&
                        it.parameterTypes[3] == Int::class.javaPrimitiveType &&
                        (it.parameterTypes.size < 5 || it.parameterTypes[4] == String::class.java)
            }?.parameterTypes?.size ?: return@runCatching logE(msg = "无法找到processor的构造函数")

            // 3. 找到 build 方法,用于生成setting item 列表
            val buildMethod = methods.singleOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType == List::class.java
            }?: return@runCatching logE(msg = "无法找到build方法")

            // 4. 找到点击事件方法
            val listeners = processorClass.getMethods(false).filter {
                it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == "kotlin.jvm.functions.Function0"
            }.sortedBy { it.name }

            if (listeners.size > 2) {
                throw IllegalStateException("listeners.size > 2, count=${listeners.size}")
            }

            val onClickMethod = listeners.first()

            // 5. hook build 方法,插入自定义 item
            val hooker = afterHook { param ->
                val context = param.args.firstOrNull() as? Context? ?: return@afterHook
                val result = param.result as? MutableList<*> ?: return@afterHook

                // 获取一个宿主上的图标资源
                val resId = context.resources.getIdentifier(
                    "qui_setting", "drawable", context.packageName
                )

                //  创建一个设置项（Setting Item）实例
                val settingItem = processorClass.new(
                    *listOf(
                        context,
                        R.id.setting2Activity_settingEntryItem,
                        "TCQT",
                        resId,
                        ""
                    ).take(processorArgCount).toTypedArray()
                )

                // 创建点击事件代理
                bindClickAction(settingItem, onClickMethod, context)

                // 把入口插入到 Group 中
                insertGroup(result, settingItem, isNewSetting)
            }

            buildMethod.hookMethod(hooker)

        }.onFailure { logE(msg = "模块入口创建失败", cause = it) }
    }

    /**
     * 设置点击回调（通过反射调用 onClickMethod）
     */
    private fun bindClickAction(item: Any, onClickMethod: Method, context: Context) {
        val function0Class = onClickMethod.parameterTypes.first()
        val unit = XpClassLoader.hostClassLoader.loadClass("kotlin.Unit")?.fieldValue("INSTANCE") ?: Unit

        val proxy = Proxy.newProxyInstance(XpClassLoader.hostClassLoader, arrayOf(function0Class)) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    browserClass ?: return@runCatching logE(msg = "无法加载内置浏览器类")

                    val intent = Intent(context, browserClass).apply {
                        putExtra("fling_action_key", 2)
                        putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                        putExtra("url", "http://${TCQTSetting.settingUrl}")
                        putExtra("hide_more_button", true)
                        putExtra("hide_operation_bar", true)
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

        // 绑定点击事件
        onClickMethod.invoke(item, proxy)
    }

    /**
     * 将设置项插入到设置页的分组容器中（Group 由宿主的设置框架定义）
     * 通过反射构造 Group 实例并插入到列表指定位置
     */
    private fun insertGroup(result: MutableList<*>, item: Any, isNewSetting: Boolean) {
        val groupClass = result.firstOrNull()?.javaClass ?: return
        val groupConstructor = groupClass.getConstructor(
            List::class.java,
            CharSequence::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType,
            XpClassLoader.hostClassLoader.loadClass("kotlin.jvm.internal.DefaultConstructorMarker")
        )

        val group = groupConstructor.newInstance(listOf(item), null, null, 6, null)
        val insertIndex = if (isNewSetting) 1 else 0
        result.invoke("add", insertIndex, group)
    }

    companion object {

        val browserClass by lazy {
            XpClassLoader.load("com.tencent.mobileqq.activity.QQBrowserDelegationActivity")
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
