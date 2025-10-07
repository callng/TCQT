package com.owo233.tcqt.hooks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.hooks.base.resInjection
import com.owo233.tcqt.impl.TicketManager
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.CalculationUtils
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.fieldValue
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.invoke
import com.owo233.tcqt.utils.new
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RegisterAction
class AddModuleEntrance : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingFragment")
            ?: error("找不到MainSettingFragment类,无法创建模块入口!!!")

        val oldEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider")
        val newEntry =
            XpClassLoader.load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider")

        if (oldEntry == null && newEntry == null) {
            error("找不到SettingConfigProvider,无法创建模块入口!!!")
        }

        // 创建入口
        createEntries(oldEntry ?: newEntry, oldEntry == null)
    }

    /**
     * 在设置页插入多个自定义入口
     *
     * @param settingConfigProviderClass SettingConfigProvider 类
     * @param isNewSetting 是否为新版设置页
     */
    @SuppressLint("DiscouragedApi")
    private fun createEntries(settingConfigProviderClass: Class<*>?, isNewSetting: Boolean) {
        settingConfigProviderClass ?: return

        runCatching {
            val methods = settingConfigProviderClass.getMethods(false)

            // 1. 查找返回类型为 processor.* 的第一个方法，获取其返回值类型
            val processorClass = methods.firstOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType.name.startsWith("com.tencent.mobileqq.setting.processor")
            }?.returnType ?: return@runCatching Log.e("无法找到返回 processor.* 类")

            // 2. 找到合适的构造函数, 用于创建item
            val processorArgCount = processorClass.constructors.firstOrNull {
                it.parameterTypes.size < 6 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        it.parameterTypes[2] == CharSequence::class.java &&
                        it.parameterTypes[3] == Int::class.javaPrimitiveType &&
                        (it.parameterTypes.size < 5 || it.parameterTypes[4] == String::class.java)
            }?.parameterTypes?.size ?: return@runCatching Log.e("无法找到processor的构造函数")

            // 3. 找到 build 方法,用于生成setting item 列表
            val buildMethod = methods.singleOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType == List::class.java
            }?: return@runCatching Log.e("无法找到build方法")

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

            // 5. hook build 方法,插入自定义 items
            val hooker = afterHook { param ->
                val context = param.args.firstOrNull() as? Context? ?: return@afterHook
                val result = param.result as? MutableList<*> ?: return@afterHook

                resInjection(context)

                // 非调试版本时排除仅调试版本显示的入口
                val filteredConfigs = entryConfigs.filter { config ->
                    !config.debugOnly || TCQTBuild.DEBUG
                }

                // 为每个配置创建设置项
                val settingItems = filteredConfigs.map { config ->
                    createSettingItem(context, config, processorClass, processorArgCount, onClickMethod)
                }

                // 把所有入口插入到 Group 中
                insertGroups(result, settingItems, isNewSetting)
            }

            buildMethod.hookMethod(hooker)

        }.onFailure { Log.e("模块入口创建失败", it) }
    }

    /**
     * 创建单个设置项
     */
    @SuppressLint("DiscouragedApi")
    private fun createSettingItem(
        context: Context,
        config: SettingEntryConfig,
        processorClass: Class<*>,
        processorArgCount: Int,
        onClickMethod: Method
    ): Any {
        // 获取图标资源
        val resId = context.resources.getIdentifier(
            config.iconName, "drawable", context.packageName
        )

        // 创建设置项实例
        val settingItem = processorClass.new(
            *listOf(
                context,
                config.id,
                config.title,
                resId,
                null
            ).take(processorArgCount).toTypedArray()
        )

        // 绑定点击事件
        bindClickAction(settingItem, onClickMethod, config.onClick, context)

        return settingItem
    }

    /**
     * 设置点击回调（通过反射调用 onClickMethod）
     */
    private fun bindClickAction(
        item: Any,
        onClickMethod: Method,
        clickListener: (Context) -> Unit,
        context: Context
    ) {
        val function0Class = onClickMethod.parameterTypes.first()
        val unit = XpClassLoader.hostClassLoader.loadClass("kotlin.Unit")?.fieldValue("INSTANCE") ?: Unit

        val proxy = Proxy.newProxyInstance(XpClassLoader.hostClassLoader, arrayOf(function0Class)) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    clickListener(context)
                }.onFailure { Log.e("设置入口点击事件执行失败", it) }
            }
            unit
        }

        // 绑定点击事件
        onClickMethod.invoke(item, proxy)
    }

    /**
     * 将多个设置项插入到设置页的分组容器中（Group 由宿主的设置框架定义）
     * 通过反射构造 Group 实例并插入到列表指定位置
     */
    private fun insertGroups(result: MutableList<*>, items: List<Any>, isNewSetting: Boolean) {
        val groupClass = result.firstOrNull()?.javaClass ?: return
        val groupConstructor = groupClass.getConstructor(
            List::class.java,
            CharSequence::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType,
            XpClassLoader.hostClassLoader.loadClass("kotlin.jvm.internal.DefaultConstructorMarker")
        )

        val group = groupConstructor.newInstance(items, null, null, 6, null)
        val insertIndex = if (isNewSetting) 1 else 0
        result.invoke("add", insertIndex, group)
    }

    // 统一入口配置
    private val entryConfigs = listOf(
        SettingEntryConfig(
            id = R.id.setting2Activity_settingEntryItem,
            title = TCQTBuild.APP_NAME,
            iconName = "qui_setting",
            onClick = ::openTCQTSettings
        ),
        SettingEntryConfig(
            id = R.id.check_ban_url,
            title = "历史冻结记录 (显示空白则重新进入)",
            iconName = "qui_tuning",
            onClick = ::openBanRecordQuery
        ),
        SettingEntryConfig(
            id = R.id.account_get_ticket,
            title = "复制账号票据 (高风险行为)",
            iconName = "qui_check_account",
            debugOnly = true,
            onClick = ::copyTicket
        )
    )

    // 复制账号票据
    private fun copyTicket(context: Context) {
        val uin = "${QQInterfaces.currentUin}"
        val uid = QQInterfaces.currentUid
        val ticket = TicketManager.getA2AndD2()
        val superKey = TicketManager.getSuperKey()
        val stWeb = TicketManager.getStweb()
        val (superToken, authToken) = CalculationUtils.getSuperToken(superKey).let { st ->
            st to CalculationUtils.getAuthToken(st)
        }

        //A2 -> 010A _TGT, D2 -> 0143, D2Key -> 0305 sessionKey
        val info = """
            这是账号票据信息
            泄露会导致账号被盗!!!

            请及时清空剪切板中的内容
            以免被第三方APP读取!!!

            Uin: $uin

            Uid: $uid

            A2: ${ticket.a2}

            D2: ${ticket.d2.toHexString(true)}

            D2Key: ${ticket.d2Key.toHexString(true)}

            StWeb: $stWeb

            SuperKey: $superKey
 
            SuperToken: $superToken

            AuthToken: $authToken
        """.trimIndent()

        context.copyToClipboard(info, true)
    }

    // 冻结记录查询
    private fun openBanRecordQuery(context: Context) {
        browserClass?.let {
            context.startActivity(
                Intent(context, it).apply {
                    putExtra("fling_action_key", 2)
                    putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                    putExtra("useDefBackText", true)
                    putExtra("param_force_internal_browser", true)
                    putExtra("url", "https://m.q.qq.com/a/s/07befc388911b30c2359bfa383f2d693")
                }
            )
        }
    }

    // 模块设置页
    private fun openTCQTSettings(context: Context) {
        browserClass?.let {
            context.startActivity(
                Intent(context, it).apply {
                    putExtra("fling_action_key", 2)
                    putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                    putExtra("url", "http://${TCQTSetting.getSettingUrl()}")
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
            )
        }
    }

    companion object {

        val browserClass by lazy {
            XpClassLoader.load("com.tencent.mobileqq.activity.QQBrowserActivity")
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}

data class SettingEntryConfig(
    val id: Int,
    val title: String,
    val iconName: String = "qui_setting",
    val debugOnly: Boolean = false,
    val onClick: (Context) -> Unit
)
