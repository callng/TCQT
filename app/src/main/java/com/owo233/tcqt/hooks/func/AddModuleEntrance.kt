package com.owo233.tcqt.hooks.func

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
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
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.impl.TicketManager
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.ui.CommonContextWrapper.Companion.toMaterialContext
import com.owo233.tcqt.utils.CalculationUtils
import com.owo233.tcqt.utils.FuzzyClassKit
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.getIntField
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isNotStatic
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.reflect.fieldValue
import com.owo233.tcqt.utils.reflect.getFields
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.new
import com.tencent.mobileqq.utils.DialogUtil
import com.tencent.mobileqq.utils.QQCustomDialog
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RegisterAction
@RegisterSetting(
    key = "add_module_entrance.boolean.ShowAttachedEntries",
    name = "显示附加工具入口",
    type = SettingType.BOOLEAN,
    defaultValue = "false",
    desc = "在宿主设置页面额外显示模块附加工具入口",
    uiTab = "高级"
)
class AddModuleEntrance : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        // 设置页入口
        runCatching {
            val mainClass = loadOrThrow("com.tencent.mobileqq.setting.main.MainSettingFragment")
            val (entryClass, isNewProvider) = resolveSettingProvider(mainClass)
            createEntries(entryClass, isNewProvider)
        }.onFailure {
            Log.e("创建模块设置入口失败", it)
        }

        // 首页 + 入口
        runCatching {
            plusMenu()
        }.onFailure {
            Log.e("添加Plus菜单入口失败", it)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun plusMenu() {
        val menuItemId = 686617
        val resId = HookEnv.hostAppContext.resources.getIdentifier(
            "qui_setting",
            "drawable",
            HookEnv.hostAppPackageName
        )
        val entryMenuItem = loadOrThrow($$"com.tencent.widget.PopupMenuDialog$MenuItem")
            .getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            .newInstance(
                menuItemId,
                TCQTBuild.APP_NAME,
                TCQTBuild.APP_NAME,
                resId
            )

        loadOrThrow("com.tencent.widget.PopupMenuDialog")
            .hookBeforeMethod(
                "conversationPlusBuild",
                Activity::class.java,
                List::class.java,
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener"),
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnDismissListener")
            ) { param ->
                param.args[1] = listOf(entryMenuItem) + param.args[1] as List<*>
            }

        val onClick = FuzzyClassKit.findMethodByClassName(
            "com.tencent.mobileqq.activity.recent"
        ) { _, method ->
            method.name == "onClickAction" && method.paramCount == 1 &&
                    method.parameterTypes[0].name.contains("MenuItem")
        } ?: error("plusMenu: 找不到符合的onClickAction方法,无法设置点击执行过程!")

        onClick.hookBeforeMethod { param ->
            if (param.args[0].getIntField("id") == menuItemId) {
                openTCQTSettings(HookEnv.hostAppContext)
                param.result = Unit
            }
        }
    }

    private fun resolveSettingProvider(mainFragmentClass: Class<*>): Pair<Class<*>, Boolean> {
        val candidates = listOf(
            "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
            "com.tencent.mobileqq.setting.main.MainSettingConfigProvider"
        )

        val entryClass = candidates
            .firstNotNullOfOrNull { name -> load(name) }
            ?: inferProviderFromField(mainFragmentClass)
            ?: error("未找到MainSettingFragment类中被混淆的Provider,无法创建模块设置入口!")

        val isNewProvider = entryClass.name != candidates.last()
        return entryClass to isNewProvider
    }

    private fun inferProviderFromField(clz: Class<*>): Class<*>? {
        return clz.getFields(false)
            .firstOrNull { it.isNotStatic && it.type != Boolean::class.javaPrimitiveType }
            ?.type
            ?.name
            ?.let { load(it) }
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

                ResourcesUtils.injectResourcesToContext(context.resources)

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
        DialogUtil.createDialogWithCheckBox(
            context,
            0,
            "安全提醒",
            "账号票据信息（A2/D2/SuperKey）等同于您的登录密码。一旦复制并泄露，攻击者可绕过身份验证直接接管您的账号。${TCQTBuild.APP_NAME} 仅提供调试支持，不对因用户主动分享数据或被第三方APP读取剪切板内容导致的资产损失、隐私泄露承担任何法律责任。",
            "我已深知泄露风险，并自愿承担后续后果",
            false,
            "取消",
            "复制",
            null,
            { dialog, _ ->
                if ((dialog as QQCustomDialog).checkBoxState) {
                    val uin = QQInterfaces.currentUin
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
                } else {
                    Toasts.error("勾选框在等待你临幸 (｡•ˇ‸ˇ•｡)")
                }
            },
            { dialog, _ ->
                dialog.dismiss()
            }
        ).apply {
            setCanceledOnTouchOutside(true)
        }.show()
    }

    private fun openBanRecordQuery(context: Context) {
        browserClass.let {
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
        browserClass.let {
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

    private fun showInfoCardDialog(ctx: Context) {
        val context = ctx.toMaterialContext()

        val editText = EditText(context).apply {
            hint = "输入QQ号或群号"

            val paddingDp = 16
            val density = context.resources.displayMetrics.density
            val paddingPx = (paddingDp * density).toInt()
            setSingleLine()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        AlertDialog.Builder(context).apply {
            setTitle("Open the card")
            setView(editText)
            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            setNeutralButton("Group") { dialog, _ ->
                val uin = editText.text.toString().trim()
                if (uin.isEmpty()) {
                    Toasts.error("请输入群号")
                    return@setNeutralButton
                }
                try {
                    if (uin.toLong() < 10000) {
                        Toasts.error("请输入正确的群号")
                        return@setNeutralButton
                    }
                } catch (_: NumberFormatException) {
                    Toasts.error("请输入正确的群号")
                    return@setNeutralButton
                }
                dialog.dismiss()
                openGroupInfoCard(context, uin)
            }
            setPositiveButton("User") { dialog, _ ->
                val uin = editText.text.toString().trim()
                if (uin.isEmpty()) {
                    Toasts.error("请输入QQ号")
                    return@setPositiveButton
                }
                try {
                    if (uin.toLong() < 10000) {
                        Toasts.error("请输入正确的账号")
                        return@setPositiveButton
                    }
                } catch (_: NumberFormatException) {
                    Toasts.error("请输入正确的账号")
                    return@setPositiveButton
                }
                dialog.dismiss()
                openUserInfoCard(context, uin)
            }
        }.create().show()
    }

    private fun openUserInfoCard(context: Context, uin: String) {
        val allInOne = load(
            "com.tencent.mobileqq.profilecard.data.AllInOne"
        ) ?: run {
            Toasts.error("接口异常")
            return
        }
        val newAllInOne = allInOne.new(uin, 83) as Parcelable

        val mActivity = if (HookEnv.isQQ())
            "com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity" else
                "com.tencent.mobileqq.profilecard.activity.TimFriendProfileCardActivity"
        if (load(mActivity) == null) {
            Toasts.error("接口异常")
            return
        }

        val intent = Intent().apply {
            setComponent(ComponentName(context, mActivity))
            putExtra("key_is_friend_profile_card", true)
            putExtra("fling_action_key", 2)
            putExtra("AllInOne", newAllInOne)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        context.startActivity(intent)
    }

    private fun openGroupInfoCard(context: Context, uin: String) {
        val mActivity = "com.tencent.mobileqq.activity.QPublicFragmentActivity"
        val mFragment = load("com.tencent.mobileqq.troop.troopcard.reborn.TroopInfoCardFragment")
        if (load(mActivity) == null || mFragment == null) {
            Toasts.error("接口异常")
            return
        }

        val intent = Intent().apply {
            setComponent(ComponentName(context, mActivity))
            setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra("fling_action_key", 2)
            putExtra("keyword", uin)
            putExtra("authSig", "") // 风控, 通过这样的方式打开资料卡申请加群, 请求可能会被屏蔽
            putExtra("troop_uin", uin)
            putExtra("vistor_type", 2)
            putExtra("public_fragment_class", mFragment.name)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        context.startActivity(intent)
    }

    companion object {
        val browserClass by lazy {
            loadOrThrow("com.tencent.mobileqq.activity.QQBrowserActivity")
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
            id = R.id.open_info_card,
            title = "打开资料卡片",
            iconName = "qui_tuning",
            extraEntry = true,
            groupTag = "TCQT_OtherSettingEntry",
            groupTitle = "TCQT工具",
            onClick = ::showInfoCardDialog
        ),
        SettingEntryConfig(
            id = R.id.check_ban_url,
            title = "历史冻结记录",
            iconName = "qui_tuning",
            extraEntry = true,
            groupTag = "TCQT_OtherSettingEntry",
            groupTitle = "TCQT小工具",
            onClick = ::openBanRecordQuery
        ),
        SettingEntryConfig(
            id = R.id.account_get_ticket,
            title = "复制账号票据",
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
