package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.config.BooleanSetting
import com.owo233.tcqt.generated.GeneratedActionList
import com.owo233.tcqt.ui.settings.FeatureCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 「声明即注册」的**运行时**断言：真实功能实例的 `settings` 必须非空。
 *
 * ## 为什么必须加载真实类
 *
 * 本仓库此前所有护栏都只扫源码文本（`libs:qqinterface` 是 `compileOnly`，
 * 单测默认加载不了功能类）。于是出现了一整类测不到的缺陷：**源码里明明写着
 * `intOption(...)`，运行时 `settings` 却是空的** —— 设置界面就只剩功能开关。
 * 真机反馈的正是这个症状。
 *
 * 现在 `app/build.gradle.kts` 给单测补上了 `qqinterface` 与
 * `androidx.constraintlayout`，因此可以真正拿到每个注册功能的单例，
 * 直接读它的 `settings`。
 *
 * ## 判据
 *
 * 对 KSP 注册清单里的每个类：
 * - 能取到实例（`INSTANCE` 或反射构造）；
 * - 源文件里写了 N 个 `xxxOption(...)`，运行时 `settings.size` 就必须是 N。
 *
 * 这条等式同时覆盖两个方向：漏注册（运行时少）与幽灵声明（运行时多）。
 */
class RuntimeSettingsRegistrationTest {

    private val optionCtor = Regex(
        "\\b(?:booleanOption|intOption|multiIntOption|sliderOption|stringOption|pngIconOption)\\s*\\("
    )

    /** 取注册单例：Kotlin `object` 直接读 `INSTANCE`，普通类走无参构造。 */
    private fun instanceOf(cls: Class<*>): Any? = runCatching {
        cls.getField("INSTANCE").get(null)
    }.getOrElse {
        runCatching { cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }
            .getOrNull()
    }

    private fun registeredClasses(): List<Class<*>> =
        GeneratedActionList.ACTIONS.toList()

    @Test
    fun `复读图标在目录中保留 PNG 选择器元数据`() {
        val repeat = FeatureCatalog.getAllFeatures().single { it.key == "repeat_message" }
        val icon = repeat.textAreas.single { it.key == "repeat_message.icon" }
        assertTrue(icon.isPngIcon, "PNG 配置不能渲染成 Base64 文本输入框")
    }

    @Test
    fun `每个功能的运行时 settings 数量等于源码里的 xxxOption 声明数`() {
        val problems = mutableListOf<String>()

        registeredClasses().forEach { cls ->
            val instance = instanceOf(cls)
            if (instance == null) {
                problems += "${cls.simpleName}: 取不到实例"
                return@forEach
            }
            if (instance !is ActionSpec) {
                problems += "${cls.simpleName}: 不是 ActionSpec"
                return@forEach
            }

            val file =
                SourceScanner.mainKotlinFiles().firstOrNull { it.name == "${cls.simpleName}.kt" }
            if (file == null) {
                problems += "${cls.simpleName}: 找不到源文件"
                return@forEach
            }

            val declared =
                optionCtor.findAll(SourceScanner.stripComments(SourceScanner.read(file))).count()
            val actual = instance.settings.size

            if (actual != declared) {
                problems += "${cls.simpleName}: 源码声明 $declared 项，运行时注册 $actual 项" +
                        "（key=${instance.key}）"
            }
        }

        assertTrue(
            problems.isEmpty(),
            "「声明即注册」被破坏 —— 设置界面会丢掉对应的文本框/多选/单选/滑块：\n" +
                    problems.joinToString("\n"),
        )
    }

    @Test
    fun `至少存在一个带配置项的功能（防止断言被空集悄悄通过）`() {
        val withSettings = registeredClasses()
            .mapNotNull { instanceOf(it) as? ActionSpec }
            .count { it.settings.isNotEmpty() }

        assertTrue(
            withSettings >= 20,
            "带配置项的功能只剩 $withSettings 个 —— 这个数字骤降说明注册链路断了，" +
                    "而不是功能真的不需要配置项",
        )
    }

    /**
     * 直接复现设置界面的取数路径：`FeatureCatalog.getAllFeatures()` 是
     * `SettingViewModel` 唯一的数据源，而 `SettingScreen` 的 `hasDetails`
     * 与展开箭头只看 `optionGroups` / `sliders` / `textAreas` 是否为空。
     *
     * 不变量：**目录里某功能的可渲染控件数 == 该功能 `settings` 里未隐藏的项数**。
     * 本用例跑通 == "设置界面里带选项的功能一定能展开出选项"。
     */
    @Test
    fun `目录里带配置项的功能必须带出可渲染的选项`() {
        val catalog = FeatureCatalog.getAllFeatures().associateBy { it.key }
        assertTrue(catalog.isNotEmpty(), "功能目录为空")

        val features = FeatureCatalog.getAllFeatures()
        val withControls = features.count {
            it.optionGroups.isNotEmpty() || it.textAreas.isNotEmpty() || it.sliders.isNotEmpty()
        }
        if (withControls == 0) {
            // 把真实运行时数据 dump 出来：症状是"设置界面只剩开关"，必须能一眼看出
            // settings 里到底是什么类型、isHide 是什么值。
            val sample = registeredClasses()
                .mapNotNull { instanceOf(it) as? ActionSpec }
                .filter { it.settings.isNotEmpty() }
                .take(4)
                .joinToString("\n") { a ->
                    "  ${a.key}: " + a.settings.joinToString(", ") {
                        "${it::class.qualifiedName}(isHide=${it.isHide})"
                    }
                }
            val catalogSample = features
                .filter { it.optionGroups.isNotEmpty() || it.textAreas.isNotEmpty() || it.sliders.isNotEmpty() }
                .take(4)
                .joinToString(", ") { it.key }
            throw AssertionError(
                "目录里 0 个功能带可渲染选项 —— 设置界面只剩开关。\n" +
                        "运行时 settings 抽样：\n$sample\n" +
                        "目录里带控件的功能抽样：[$catalogSample]\n" +
                        "功能总数=${features.size}"
            )
        }
        assertTrue(withControls >= 20, "目录里只有 $withControls 个功能带可渲染选项")

        val problems = mutableListOf<String>()
        registeredClasses().forEach { cls ->
            val instance = instanceOf(cls) as? ActionSpec ?: return@forEach
            val entry = catalog[instance.key] ?: return@forEach

            // BooleanSetting 按契约**从不渲染**成界面组件（它只是持久化的布尔项），
            // 因此"可渲染"的口径要把 isHide 与 BooleanSetting 都排除掉。
            val visible = instance.settings.count { !it.isHide && it !is BooleanSetting }
            val rendered = entry.optionGroups.size + entry.textAreas.size + entry.sliders.size

            if (visible != rendered) {
                problems += "${cls.simpleName}(${instance.key}): settings 里可见 ${visible} 项，" +
                        "目录里可渲染 ${rendered} 项"
            }
        }

        assertTrue(
            problems.isEmpty(),
            "设置界面会丢掉这些功能的额外配置项：\n" + problems.joinToString("\n"),
        )
    }
}
