package com.owo233.tcqt.hooks.func

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.R
import com.owo233.tcqt.activity.SettingActivity
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.impl.EasyLoginException
import com.owo233.tcqt.impl.TicketManager
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.ui.CommonContextWrapper.Companion.toCompatibleContext
import com.owo233.tcqt.utils.CalculationUtils
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.isNotStatic
import com.owo233.tcqt.utils.hook.paramCount
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.fieldValue
import com.owo233.tcqt.utils.reflect.getFields
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.new
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import oicq.wlogin_sdk.request.WTLoginRecordSnapshot
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RegisterAction
class AddModuleEntrance : AlwaysRunAction() {

    override val key: String get() = "add_module_entrance"
    override val name: String get() = "显示附加工具入口"
    override val desc: String get() = "在宿主设置页面额外显示模块附加工具入口"
    override val uiTab: String get() = "高级"

    private var cachedProcessorInfo: ProcessorInfo? = null

    // ── Entry Configurations ───────────────────────────────────────────

    private val entryConfigs by lazy {
        buildList {
            add(
                SettingEntryConfig(
                    id = R.id.setting2Activity_settingEntryItem,
                    title = TCQTBuild.APP_NAME,
                    groupTag = "TCQT_SettingEntry",
                    onClick = ::openTCQTSettings
                )
            )
            add(
                SettingEntryConfig(
                    id = R.id.open_info_card,
                    title = "打开资料卡片",
                    iconName = "qui_tuning",
                    extraEntry = true,
                    groupTag = "TCQT_OtherSettingEntry",
                    groupTitle = "TCQT工具",
                    onClick = ::showInfoCardDialog
                )
            )
            if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_70)) {
                add(
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
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onRun(app: Application, process: ActionProcess) {
        runCatching { hookSettingEntries() }
            .onFailure { Log.e("创建模块设置入口失败", it) }
    }

    // ── Settings Page Entries ──────────────────────────────────────────

    private fun hookSettingEntries() {
        val mainClass = loadOrThrow("com.tencent.mobileqq.setting.main.MainSettingFragment")
        val (providerClass, isNew) = resolveSettingProvider(mainClass)

        val buildMethod = providerClass.findBuildMethod()
            ?: return Log.e("没有找到 build 方法,无法创建模块设置入口!!!")

        buildMethod.hookAfter { param ->
            val context = param.args.firstOrNull() as? Context ?: return@hookAfter
            val result = param.result as? MutableList<*> ?: return@hookAfter
            val processorInfo = resolveProcessorInfo(result) ?: return@hookAfter

            ResourcesUtils.injectResourcesToContext(context.resources)

            val showAttached = TCQTSetting.getBoolean(key)

            entryConfigs
                .filter { !it.extraEntry || showAttached }
                .groupBy { it.groupTag.orEmpty() }
                .values
                .map { group ->
                    val title = group.firstNotNullOfOrNull { it.groupTitle }
                    val items = group.map { it.toSettingItem(context, processorInfo) }
                    title to items
                }
                .asReversed()
                .forEach { (title, items) -> insertGroup(result, items, isNew, title) }
        }
    }

    private fun resolveSettingProvider(mainFragmentClass: Class<*>): Pair<Class<*>, Boolean> {
        val knownProviders = listOf(
            "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
            "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
        )

        val providerClass = knownProviders.firstNotNullOfOrNull(::load)
            ?: mainFragmentClass.inferProviderClass()
            ?: error("未找到MainSettingFragment类中被混淆的Provider,无法创建模块设置入口!")

        val isNew = providerClass.name == knownProviders.last() || providerClass.name !in knownProviders
        return providerClass to isNew
    }

    // ── Processor Resolution ───────────────────────────────────────────

    private fun resolveProcessorInfo(result: List<*>): ProcessorInfo? {
        cachedProcessorInfo?.let { return it }

        val processorClass = result.collectProcessorCandidates()
            .firstOrNull(::matchesProcessorSignature)
            ?: return null.also { Log.e("无法从 build() 结果中自动识别 processor 类,无法创建模块设置入口!") }

        val ctor = processorClass.constructors.first { it.parameterTypes.size in 4..5 }
        val onClick = processorClass.findOnClickMethod()
            ?: return null.also { Log.e("无法找到点击事件方法 (Function0),无法创建模块设置入口!") }

        return ProcessorInfo(processorClass, ctor.parameterTypes.size, onClick).also {
            cachedProcessorInfo = it
        }
    }

    private fun List<*>.collectProcessorCandidates(): Set<Class<*>> = buildSet {
        this@collectProcessorCandidates.filterNotNull().forEach { item ->
            item.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                when (val value = runCatching { field.get(item) }.getOrNull()) {
                    is Array<*> -> value.mapNotNullTo(this) { it?.javaClass }
                    is Collection<*> -> value.mapNotNullTo(this) { it?.javaClass }
                }
            }
        }
    }

    // ── Setting Item Factory ───────────────────────────────────────────

    @SuppressLint("DiscouragedApi")
    private fun SettingEntryConfig.toSettingItem(context: Context, info: ProcessorInfo): Any {
        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        val args = arrayOf(context, id, title, resId, null).take(info.argCount).toTypedArray()
        return info.clazz.new(*args).also { it.bindClick(info.onClickMethod, onClick, context) }
    }

    private fun Any.bindClick(method: Method, listener: (Context) -> Unit, context: Context) {
        val function0Class = method.parameterTypes.first()
        val unitInstance = HookEnv.hostClassLoader
            .loadClass("kotlin.Unit")?.fieldValue("INSTANCE") ?: Unit

        val proxy = Proxy.newProxyInstance(HookEnv.hostClassLoader, arrayOf(function0Class)) { _, m, _ ->
            if (m.name == "invoke") {
                runCatching { listener(context) }
                    .onFailure { Log.e("设置入口点击事件执行失败", it) }
            }
            unitInstance
        }
        method.invoke(this, proxy)
    }

    private fun insertGroup(
        result: MutableList<*>,
        items: List<Any>,
        isNewSetting: Boolean,
        title: CharSequence?
    ) {
        val groupClass = result.firstOrNull()?.javaClass ?: return
        val markerClass = HookEnv.hostClassLoader.loadClass("kotlin.jvm.internal.DefaultConstructorMarker")

        val group = groupClass
            .getConstructor(List::class.java, CharSequence::class.java, CharSequence::class.java,
                Int::class.javaPrimitiveType, markerClass)
            .newInstance(items, title, null, if (title != null) 4 else 6, null)

        result.invoke("add", if (isNewSetting) 1 else 0, group)
    }

    // ── Ticket Copying ─────────────────────────────────────────────────

    private fun copyTicket(ctx: Context) {
        val context = ctx.toCompatibleContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_copy_ticket_warning, null)
        val etConfirm = view.findViewById<EditText>(R.id.et_confirm_input)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnCopy = view.findViewById<Button>(R.id.btn_copy)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnCopy.setOnClickListener {
            val userInput = etConfirm.text.toString().trim()
            val expectedText = "我已知晓风险"

            if (userInput != expectedText) {
                etConfirm.error = "请输入“${expectedText}”以确认操作"
                return@setOnClickListener
            }

            dialog.dismiss()
            ModuleScope.launchIO { loginAndCopyTicket(context) }
        }

        dialog.show()

        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private suspend fun loginAndCopyTicket(context: Context) {
        val snapshot = try {
            TicketManager.easyLogin()
            delay(233L)
            TicketManager.getWTLoginRecordSnapshot()
        } catch (_: TimeoutCancellationException) {
            return Toasts.error("easyLogin Timeout")
        } catch (e: EasyLoginException) {
            Log.e("easyLogin fail", e)
            return Toasts.error(e.message)
        } catch (_: UnsupportedOperationException) {
            return Toasts.error("版本要求=> 9.2.70")
        }

        context.copyToClipboard(snapshot.formatTicketInfo(), true)
    }

    private fun WTLoginRecordSnapshot.formatTicketInfo(): String = buildString {
        appendLine("这是账号票据信息")
        appendLine("泄露会导致账号被盗!!!\n")
        appendLine("请及时清空剪切板中的内容")
        appendLine("以免被第三方APP读取!!!\n")
        appendLine("uin: $uin\n")
        appendLine("uid: ${QQInterfaces.currentUid}\n")
        appendLine("guid: ${QQInterfaces.guid}\n")
        appendLine("a1: ${a1.toHexString(true)} // 0106 en_A1\n")
        appendLine("a1Key: ${a1Key.toHexString(true)} // 010C TGTGTKey\n")
        appendLine("noPicSig: ${noPicSig.toHexString(true)} // 016A\n")
        appendLine("a2: ${a2.toHexString(true)} // 010A TGT\n")
        appendLine("a2Key: ${a2Key.toHexString(true)} // 010D TGTKey\n")
        appendLine("d2: ${d2.toHexString(true)} // 0143\n")
        appendLine("d2Key: ${d2Key.toHexString(true)} // 0305\n")

        val stWeb = TicketManager.getStweb()
        val superKey = TicketManager.getSuperKey()
        val superToken = CalculationUtils.getSuperToken(superKey)
        val authToken = CalculationUtils.getAuthToken(superToken)

        appendLine("stWeb: $stWeb // 0103\n")
        appendLine("superKey: $superKey // 016D\n")
        appendLine("superToken: $superToken\n")
        appendLine("authToken: $authToken\n")
        appendLine("a2生成时间: $a2GenerateTime")
        appendLine("a2过期时间: $expireTime")
    }

    // ── Info Card Dialog ───────────────────────────────────────────────

    private fun showInfoCardDialog(ctx: Context) {
        val context = ctx.toCompatibleContext()

        val editText = EditText(context).apply {
            hint = "输入QQ号或群号"
            setSingleLine()
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        AlertDialog.Builder(context).apply {
            setTitle("Open the card")
            setView(editText)
            setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            setNeutralButton("Group") { _, _ ->
                editText.validateUin("请输入群号", "请输入正确的群号")
                    ?.let { openGroupInfoCard(context, it) }
            }
            setPositiveButton("User") { _, _ ->
                editText.validateUin("请输入QQ号", "请输入正确的账号")
                    ?.let { openUserInfoCard(context, it) }
            }
        }.create().show()
    }

    private fun EditText.validateUin(emptyMsg: String, invalidMsg: String): String? {
        val uin = text.toString().trim()
        if (uin.isEmpty()) return null.also { Toasts.error(emptyMsg) }
        if (runCatching { uin.toLong() < 10000 }.getOrDefault(true)) {
            return null.also { Toasts.error(invalidMsg) }
        }
        return uin
    }

    private fun openUserInfoCard(context: Context, uin: String) {
        val allInOneClass = load("com.tencent.mobileqq.profilecard.data.AllInOne")
            ?: return Toasts.error("接口异常")

        val activityClass = if (HookEnv.isQQ())
            "com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity"
        else
            "com.tencent.mobileqq.profilecard.activity.TimFriendProfileCardActivity"

        if (load(activityClass) == null) return Toasts.error("接口异常")

        val allInOne = allInOneClass.new(uin, 83) as Parcelable

        context.startActivity(Intent().apply {
            component = ComponentName(context, activityClass)
            putExtra("key_is_friend_profile_card", true)
            putExtra("fling_action_key", 2)
            putExtra("AllInOne", allInOne)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openGroupInfoCard(context: Context, uin: String) {
        val activityClass = "com.tencent.mobileqq.activity.QPublicFragmentActivity"
        val fragmentClass = load("com.tencent.mobileqq.troop.troopcard.reborn.TroopInfoCardFragment")
        if (load(activityClass) == null || fragmentClass == null) return Toasts.error("接口异常")

        context.startActivity(Intent().apply {
            component = ComponentName(context, activityClass)
            flags = Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("fling_action_key", 2)
            putExtra("keyword", uin)
            putExtra("authSig", "")
            putExtra("troop_uin", uin)
            putExtra("vistor_type", 2)
            putExtra("public_fragment_class", fragmentClass.name)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Navigation ─────────────────────────────────────────────────────

    private fun openTCQTSettings(ctx: Context? = null) {
        runCatching {
            val activity = ctx ?: QQInterfaces.topActivity
            ModuleScope.launchMain {
                activity.startActivity(Intent(activity, SettingActivity::class.java))
            }
        }.onFailure {
            Toasts.error("需要重新启动${HookEnv.appName}")
            HookEnv.resetApp()
        }
    }

    // ── Reflection Helpers ─────────────────────────────────────────────

    private companion object {

        private fun Class<*>.inferProviderClass(): Class<*>? =
            getFields(false)
                .firstOrNull { it.isNotStatic && it.type != Boolean::class.javaPrimitiveType }
                ?.type
                ?.let { load(it.name) }

        private fun Class<*>.findBuildMethod(): Method? =
            declaredMethods.find {
                it.paramCount == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        List::class.java.isAssignableFrom(it.returnType)
            }

        private fun Class<*>.findOnClickMethod(): Method? =
            declaredMethods.find {
                it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == "kotlin.jvm.functions.Function0"
            }

        private fun matchesProcessorSignature(cls: Class<*>): Boolean =
            cls.constructors.any { ctor ->
                val t = ctor.parameterTypes
                t.size in 4..5 &&
                        t[0] == Context::class.java &&
                        t[1] == Int::class.javaPrimitiveType &&
                        t[2] == CharSequence::class.java &&
                        t[3] == Int::class.javaPrimitiveType &&
                        (t.size == 4 || t[4] == String::class.java)
            }
    }

    // ── Data Models ────────────────────────────────────────────────────

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
