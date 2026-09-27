package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 迁移到 `com.owo233.tcqt.api` 门面的功能：**派生 key 必须等于其历史 key**。
 *
 * 这是"用户已保存配置不丢失"的唯一机器保证：
 * - 旧契约的 key 是手写字符串（`TCQTSetting.getInt("fake_pic_size.type")`）；
 * - 新契约的 key 由 `Feature.key` + `Option.settingKey` 派生（`"$featureKey.$settingKey"`）。
 *
 * 若派生规则或任一 `settingKey` 拼错，用户的配置会静默读不到 —— 本测试让它编译期就红。
 *
 * 注意本测试**只做文本解析，不加载类**：这些功能类会引用宿主类（`com.tencent.*`），
 * 而宿主 stub 是 `compileOnly`，JVM 单测里加载它们会 `NoClassDefFoundError`。
 *
 * **新增迁移功能时必须往 [EXPECTED] 加一行。** 这张表是唯一的历史 key 存档，
 * 对应关系来自迁移前的 `LegacySettingKeySnapshotTest`。
 */
class DerivedKeyCompatibilityTest {

    private val featureKeyArg = Regex(
        "Feature\\s*\\([\\s\\S]{0,200}?\\bkey\\s*=\\s*\"([^\"]*)\""
    )

    private val optionArg = Regex(
        "(?:booleanOption|intOption|multiIntOption|sliderOption|stringOption)" +
                "\\s*\\(\\s*(?:settingKey\\s*=\\s*)?\"([^\"]+)\""
    )

    private fun sourceText(name: String): String {
        val file = SourceScanner.mainKotlinFiles().firstOrNull { it.name == name }
            ?: error("找不到 $name")
        return SourceScanner.stripComments(SourceScanner.read(file))
    }

    /** 文件名 → (功能 key, 该功能全部历史配置 key)。 */
    private val EXPECTED: Map<String, Pair<String, List<String>>> = mapOf(
        "FakePicSize.kt" to ("fake_pic_size" to listOf(
            "fake_pic_size.custom_height",
            "fake_pic_size.custom_width",
            "fake_pic_size.type",
        )),
        // forced_config_ids 是后来新增的配置项，不存在历史 key 需要兼容（新增项的派生 key 一律放行）
        "ForcedABTest.kt" to ("forced_to_ab" to listOf(
            "forced_to_ab.forced_config_ids",
            "forced_to_ab.mode",
        )),
        "SwitchLoginMode.kt" to ("switch_login_mode" to listOf("switch_login_mode.type")),
        "DefaultVASAttributes.kt" to ("default_vas_attrs" to listOf("default_vas_attrs.type")),
        "DisableDialog.kt" to ("disable_dialog" to listOf("disable_dialog.type")),
        "RenameBaseApk.kt" to ("rename_base_apk" to listOf("rename_base_apk.type")),
        "FakePhone.kt" to ("fake_phone" to listOf("fake_phone.string.phone")),
        "ChangePreviewTextSize.kt" to ("change_preview_text_size" to listOf(
            "change_preview_text_size.string.textSize",
        )),
        "ChangeGuid.kt" to ("change_guid" to listOf(
            "change_guid.boolean.isEnabled",
            "change_guid.string.defaultGuid",
            "change_guid.string.newGuid",
        )),
        "CustomDevice.kt" to ("custom_device" to listOf(
            "custom_device.string.device",
            "custom_device.string.manufacturer",
            "custom_device.string.model",
        )),
        "HideGrayTipText.kt" to ("hide_gray_tip_text" to listOf(
            "hide_gray_tip_text.string.saveConfig",
        )),
        "ImageCustomSummary.kt" to ("image_custom_summary" to listOf(
            "image_custom_summary.string",
            "image_custom_summary.type",
        )),
        "MMKVConfigHook.kt" to ("mmkv_config_hook" to listOf(
            "mmkv_config_hook.string.saveConfig",
        )),
        "FakeNetworkStatus.kt" to ("fake_network_status" to listOf(
            "fake_network_status.mode",
        )),
        "SignSpoof.kt" to ("share_sign_spoof" to listOf(
            "share_sign_spoof.string.customMap",
        )),
        "PicTypeEmoticon.kt" to ("pic_type_emoticon" to listOf(
            "pic_type_emoticon.type",
        )),
        "RepeatMessage.kt" to ("repeat_message" to listOf(
            "repeat_message.options",
        )),
        "MsgAntiRecall.kt" to ("msg_anti_recall" to listOf(
            "msg_anti_recall.type",
        )),
        "HideChatPanelButton.kt" to ("hide_chat_panel_button" to listOf(
            "hide_chat_panel_button.items",
        )),
        "ShowMsgInfo.kt" to ("show_msg_info" to listOf(
            "show_msg_info.format",
        )),
        "SimplifyQQSettingMe.kt" to ("simplify_qq_setting_me" to listOf(
            "simplify_qq_setting_me.type",
        )),
        "MessagingStyleNotification.kt" to ("messaging_style_notification" to listOf(
            "messaging_style_notification.options",
        )),
        // 这 6 个 key 原本以常量形式声明在 FloatingBottomBarConfigStore /
        // QQTabLocator 里；迁移后由 Feature.key + settingKey 派生。
        "LiquidGlassTabBar.kt" to ("liquid_glass_tab_bar" to listOf(
            "liquid_glass_tab_bar.blur_percent",
            "liquid_glass_tab_bar.config",
            "liquid_glass_tab_bar.implementation",
            "liquid_glass_tab_bar.mode",
            "liquid_glass_tab_bar.position",
            "liquid_glass_tab_bar.scale",
        )),
        // UnitedConfigHook 曾是这里的条目；它已按「退役动作」删除
        // （注解被注释掉、全仓库无引用），因此不再有 key 需要兼容。
    )

    /**
     * 迁移期**只读**的旧 key 白名单。
     *
     * `RepeatMessage` 把选项从 `repeat_message.type` 挪到了 `repeat_message.options`，
     * 读新 key 之前必须先探测旧 key 有没有值 —— 这个字面量是必需的兼容垫片，
     * 不是"手写的配置项声明"。
     *
     * 白名单只允许增加**有注释说明的只读兼容项**；任何新增的配置项都必须走
     * `Option.settingKey` 派生。
     */
    private val LEGACY_READ_KEYS = setOf("repeat_message.type")

    private val dottedLiteral = Regex("\"([A-Za-z0-9_]+\\.[A-Za-z0-9_.]+)\"")

    @Test
    fun `已迁移功能的派生 key 等于其历史 key`() {
        val problems = mutableListOf<String>()

        EXPECTED.forEach { (fileName, spec) ->
            val (expectedFeatureKey, expectedKeys) = spec
            val text = sourceText(fileName)

            val featureKey = featureKeyArg.find(text)?.groupValues?.get(1)
                ?: run {
                    problems += "$fileName: 找不到 Feature(key = \"...\")，说明它还没迁移到 api 门面"
                    return@forEach
                }
            if (featureKey != expectedFeatureKey) {
                problems += "$fileName: 功能开关 key 变了 [$featureKey] != [$expectedFeatureKey]"
            }

            val derived = optionArg.findAll(text)
                .map { "$featureKey.${it.groupValues[1]}" }
                .sorted()
                .toList()

            if (derived != expectedKeys.sorted()) {
                problems += "$fileName: 派生 key 与历史不一致\n        实际=$derived\n        期望=${expectedKeys.sorted()}"
            }
        }

        assertTrue(
            problems.isEmpty(),
            "派生 key 兼容性被破坏 —— 用户已保存的配置会读不到：\n" + problems.joinToString("\n")
        )
    }

    @Test
    fun `已迁移文件内不再出现派生 key 的字面量`() {
        val problems = mutableListOf<String>()

        EXPECTED.forEach { (fileName, spec) ->
            val featureKey = spec.first
            dottedLiteral.findAll(sourceText(fileName))
                .map { it.groupValues[1] }
                .filter { it.startsWith("$featureKey.") }
                .filterNot { it in LEGACY_READ_KEYS }
                .distinct()
                .forEach { problems += "$fileName -> \"$it\"" }
        }

        assertTrue(
            problems.isEmpty(),
            "派生 key 只应由 Feature.key 与 Option.settingKey 拼接产生；以下字面量是手写 key：\n" +
                    problems.joinToString("\n"),
        )
    }

    /**
     * **框架 import 面回归哨兵**（不是"必须降到 1"的承诺 —— 见 ADR-007）。
     *
     * Spec §2.1 的 G1 原本要求"新增功能只需 1 行框架 import"。S3(a) 实施前实测发现
     * 它在层次约束下**不可达**：`features/` 需要 95 个不同的 `core` 符号与 15 个
     * `host` 符号，而 spec §3.1 禁止 `api` 依赖 `host` —— 宿主能力无法经门面转发。
     * 硬做只能把 95 个符号转发进 `api`，把门面变成 `core` 的镜像。
     *
     * 因此 G1 改写为可达且可验收的 **G1'（ADR-007）**：作者必须只需 `api.*` 表达
     * **功能契约**，其余能力按需引入；**不得出现"为了接进框架而必须认识的类"**。
     * 后一条由 `AuthoringSurfaceTest` 机械验收。
     *
     * 本断言保留为**防膨胀哨兵**：`FakePicSize` 的框架 import 目前 3 个
     * （`api.*` + `core.hook.hookBefore` + `core.reflect.findMethod`）。
     * 数字变大 = 作者要认识的东西变多了，那是真回归。
     *
     * 计数口径**不含** `import com.owo233.tcqt.annotations.RegisterAction`
     * —— 注册标记不可避免，G1' 关心的是"作者要认识几个框架包"。
     */
    @Test
    fun `FakePicSize 的框架 import 面被钉住（防止作者负担膨胀）`() {
        val file = SourceScanner.mainKotlinFiles().firstOrNull { it.name == "FakePicSize.kt" }
            ?: error("找不到 FakePicSize.kt")
        val frameworkPrefix = "import com.owo233.tcqt."
        val imports = SourceScanner.read(file).lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(frameworkPrefix) }
            .filterNot { it.startsWith("${frameworkPrefix}annotations.") }
            .toList()

        assertEquals(
            3, imports.size,
            "框架 import 面发生变化：$imports\n" +
                    "变多 = 作者负担增加，是真回归；变少是好事，请同步更新本断言与 ADR-007",
        )
        // 星号与具名都算「走 api 门面」。
        // 早先这里写死了 `import com.owo233.tcqt.api.*` —— 那是过度约束：IDE 的
        // Optimize Imports 会把星号展开成具名（`api.Feature` / `api.Requires` …），
        // 语义完全相同，却会让这条断言无谓地红。
        assertTrue(
            imports.any { it.startsWith("import com.owo233.tcqt.api.") },
            "功能契约必须走 api 门面（星号或具名 import 均可）：$imports",
        )
    }
}
