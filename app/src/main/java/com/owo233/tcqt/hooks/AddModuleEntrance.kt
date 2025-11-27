package com.owo233.tcqt.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.impl.TicketManager
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.CalculationUtils
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.fieldValue
import com.owo233.tcqt.utils.getFields
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.invoke
import com.owo233.tcqt.utils.isNotStatic
import com.owo233.tcqt.utils.new
import com.owo233.tcqt.utils.paramCount
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RegisterAction
@RegisterSetting(
    key = "add_module_entrance.boolean.ShowAttachedEntries",
    name = "显示附加工具入口",
    type = SettingType.BOOLEAN,
    defaultValue = "false",
    desc = "在宿主设置页面额外显示模块附加工具入口",
    uiTab = "高级",
    uiOrder = 110
)
class AddModuleEntrance : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val mainClass = load("com.tencent.mobileqq.setting.main.MainSettingFragment")
            ?: error("找不到 MainSettingFragment类,无法创建模块设置入口!")

        val (entryClass, isNewProvider) = resolveSettingProvider(mainClass)
        createEntries(entryClass, isNewProvider)
    }

    private fun resolveSettingProvider(mainFragmentClass: Class<*>): Pair<Class<*>, Boolean> {
        val oldProvider = load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider")
        val newProvider = load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider")

        val entryClass = when {
            oldProvider != null -> oldProvider
            newProvider != null -> newProvider
            else -> {
                val field = mainFragmentClass.getFields(false)
                    .firstOrNull { it.isNotStatic && it.type != Boolean::class.javaPrimitiveType }
                    ?: error("未找到 MainSettingFragment类中被混淆的入口字段,无法创建模块设置入口!")
                load(field.type.name)!!
            }
        }

        return entryClass to (oldProvider == null)
    }

    /**
     * 在设置页插入多个自定义入口
     *
     * @param settingConfigProviderClass SettingConfigProvider 类
     * @param isNewSetting 是否为新版设置页
     */
    @SuppressLint("DiscouragedApi")
    private fun createEntries(settingConfigProviderClass: Class<*>, isNewSetting: Boolean) {
        runCatching {
            val buildMethod = findBuildMethod(settingConfigProviderClass)
                ?: return@runCatching Log.e("没有找到 build 方法,无法创建模块设置入口!!!")

            buildMethod.hookMethod(afterHook { param ->
                val context = param.args.firstOrNull() as? Context ?: return@afterHook
                val result = param.result as? MutableList<*> ?: return@afterHook

                val processorInfo = resolveProcessorInfo(result)
                    ?: return@afterHook

                ResourcesUtils.injectResourcesToContext(context, HookEnv.moduleApkPath)

                val showAttached = GeneratedSettingList.getBoolean(
                    GeneratedSettingList.ADD_MODULE_ENTRANCE_BOOLEAN_SHOWATTACHEDENTRIES
                )
                val filteredConfigs = entryConfigs.filter { config ->
                    !config.extraEntry || showAttached
                }
                val grouped = filteredConfigs
                    .groupBy { it.groupTag ?: "" }
                    .map { (tag, groupConfigs) ->
                        val title = groupConfigs.firstOrNull { it.groupTitle != null }?.groupTitle
                        val items = groupConfigs.map { config ->
                            createSettingItem(context, config, processorInfo)
                        }
                        Triple(tag, title, items)
                    }

                for ((_, title, items) in grouped.asReversed()) {
                    insertGroups(result, items, isNewSetting, title)
                }
            })
        }.onFailure { Log.e("模块设置入口创建失败", it) }
    }

    private fun findBuildMethod(cls: Class<*>): Method? {
        return cls.declaredMethods.find {
            it.paramCount == 1 &&
                    it.parameterTypes[0] == Context::class.java &&
                    List::class.java.isAssignableFrom(it.returnType)
        }
    }

    private var cachedProcessorInfo: ProcessorInfo? = null

    private fun resolveProcessorInfo(result: List<*>): ProcessorInfo? {
        if (cachedProcessorInfo != null) return cachedProcessorInfo

        val candidates = mutableSetOf<Class<*>>()
        result.forEach { item ->
            if (item == null) return@forEach
            val cls = item.javaClass
            cls.declaredFields.forEach { field ->
                field.isAccessible = true
                val value = runCatching { field.get(item) }.getOrNull()
                when (value) {
                    is Array<*> -> value.forEach { v -> v?.javaClass?.let(candidates::add) }
                    is Collection<*> -> value.forEach { v -> v?.javaClass?.let(candidates::add) }
                }
            }
        }

        val target = candidates.find { cls ->
            cls.constructors.any { ctor ->
                val types = ctor.parameterTypes
                types.size in 4..5 &&
                        types[0] == Context::class.java &&
                        types[1] == Int::class.javaPrimitiveType &&
                        types[2] == CharSequence::class.java &&
                        types[3] == Int::class.javaPrimitiveType &&
                        (types.size == 4 || types[4] == String::class.java)
            }
        } ?: return null.also {
            Log.e("无法从 build() 结果中自动识别 processor 类,无法创建模块设置入口!")
        }

        val constructor = target.constructors.first { it.parameterTypes.size in 4..5 }
        val argCount = constructor.parameterTypes.size

        val onClickMethod = target.declaredMethods.find {
            it.returnType == Void.TYPE &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name == "kotlin.jvm.functions.Function0"
        } ?: return null.also {
            Log.e("无法找到点击事件方法 (Function0),无法创建模块设置入口!")
        }

        return ProcessorInfo(target, argCount, onClickMethod).also {
            cachedProcessorInfo = it
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun createSettingItem(
        context: Context,
        config: SettingEntryConfig,
        info: ProcessorInfo
    ): Any {
        val resId = context.resources.getIdentifier(config.iconName, "drawable", context.packageName)
        val args = arrayOf(
            context,
            config.id,
            config.title,
            resId,
            null
        ).take(info.argCount).toTypedArray()

        val settingItem = info.clazz.new(*args)
        bindClickAction(settingItem, info.onClickMethod, config.onClick, context)
        return settingItem
    }

    private fun bindClickAction(
        item: Any,
        onClickMethod: Method,
        clickListener: (Context) -> Unit,
        context: Context
    ) {
        val function0Class = onClickMethod.parameterTypes.first()
        val unit = HookEnv.hostClassLoader
            .loadClass("kotlin.Unit")
            ?.fieldValue("INSTANCE") ?: Unit

        val proxy = Proxy.newProxyInstance(
            HookEnv.hostClassLoader,
            arrayOf(function0Class)
        ) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    clickListener(context)
                }.onFailure { Log.e("设置入口点击事件执行失败", it) }
            }
            unit
        }

        onClickMethod.invoke(item, proxy)
    }

    private fun insertGroups(
        result: MutableList<*>,
        items: List<Any>,
        isNewSetting: Boolean,
        title: CharSequence?
    ) {
        val groupClass = result.firstOrNull()?.javaClass ?: return
        val markerClass = HookEnv.hostClassLoader
            .loadClass("kotlin.jvm.internal.DefaultConstructorMarker")

        val groupConstructor = groupClass.getConstructor(
            List::class.java,
            CharSequence::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType,
            markerClass
        )

        val group = groupConstructor.newInstance(
            items,
            title,
            null,
            if (title != null) 4 else 6,
            null
        )

        val insertIndex = if (isNewSetting) 1 else 0
        result.invoke("add", insertIndex, group)
    }

    private fun copyTicket(context: Context) {
        val uin = "${QQInterfaces.currentUin}"
        val uid = QQInterfaces.currentUid
        val ticket = TicketManager.getA2AndD2()
        val superKey = TicketManager.getSuperKey()
        val stWeb = TicketManager.getStweb()
        val (superToken, authToken) = CalculationUtils.getSuperToken(superKey).let { st ->
            st to CalculationUtils.getAuthToken(st)
        }

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

    private fun openBanRecordQuery(context: Context) {
        browserClass?.let {
            val intent = Intent(context, it).apply {
                putExtra("fling_action_key", 2)
                putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                putExtra("useDefBackText", true)
                putExtra("param_force_internal_browser", true)
                putExtra("url", "https://m.q.qq.com/a/s/07befc388911b30c2359bfa383f2d693")
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun openTCQTSettings(context: Context) {
        browserClass?.let {
            val intent = Intent(context, it).apply {
                putExtra("fling_action_key", 2)
                putExtra("fling_code_key", this@AddModuleEntrance.hashCode())
                putExtra("url", TCQTSetting.settingUrl)
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
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    companion object {
        val browserClass by lazy {
            load("com.tencent.mobileqq.activity.QQBrowserActivity")
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    private val entryConfigs = listOf(
        SettingEntryConfig(
            id = R.id.setting2Activity_settingEntryItem,
            title = TCQTBuild.APP_NAME,
            iconName = "qui_setting",
            groupTag = "TCQT_SettingEntry",
            groupTitle = null,
            onClick = ::openTCQTSettings
        ),
        SettingEntryConfig(
            id = R.id.check_ban_url,
            title = "历史冻结记录 (显示空白则重新进入)",
            iconName = "qui_tuning",
            extraEntry = true,
            groupTag = "TCQT_OtherSettingEntry",
            groupTitle = "TCQT小工具",
            onClick = ::openBanRecordQuery
        ),
        SettingEntryConfig(
            id = R.id.account_get_ticket,
            title = "复制账号票据 (高风险行为)",
            iconName = "qui_check_account",
            extraEntry = true,
            groupTag = "TCQT_OtherSettingEntry",
            groupTitle = "TCQT工具",
            onClick = ::copyTicket
        )
    )

    private data class SettingEntryConfig(
        val id: Int,
        val title: String,
        val iconName: String = "qui_setting",
        val extraEntry: Boolean = false,
        val groupTag: String? = null,
        val groupTitle: CharSequence? = null,
        val onClick: (Context) -> Unit
    )

    private data class ProcessorInfo(
        val clazz: Class<*>,
        val argCount: Int,
        val onClickMethod: Method
    )
}
