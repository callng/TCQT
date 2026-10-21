package com.owo233.tcqt.baseline

import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.config.StringSetting
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Feature` 的「声明即注册」机制探针。
 *
 * 存在的理由：真机反馈「设置界面里功能的额外选项（文本框/多选/单选）全都不见了，
 * 只剩开关」。这条链路是
 *
 *     `by xxxOption(...)` → `Feature.register()` → `declaredOptions`
 *         → `Feature.settings` → `FeatureCatalog.getAllFeatures()` → UI
 *
 * 而 `FeatureCatalog` / `SettingsRegistry` 都只读 `action.settings`。若
 * 「声明即注册」没生效，UI 就只剩开关。本测试用**纯 JVM 的 Feature 子类**
 * 直接盯住该机制，不依赖宿主 stub（`libs:qqinterface` 是 `compileOnly`，
 * 真实功能类在 JVM 里加载不了）。
 *
 * 注意：`Option.toSetting()` 只构造数据对象，不碰 `TCQTSetting`，因此
 * 读 `settings` 在 JVM 里是安全的。
 */
class FeatureOptionRegistrationTest {

    private class Probe : Feature(key = "probe_feature", name = "探针") {
        val ints by intOption("type", "单选", defaultValue = 1, options = listOf("A", "B"))
        val multis by multiIntOption("options", "多选", options = listOf("X", "Y", "Z"))
        val strings by stringOption("text", "文本框", placeholder = "占位")
        val sliders by sliderOption("scale", "滑块", min = 0, max = 10, suffix = "%")
        val bools by booleanOption("flag", "布尔")
        val icon by pngIconOption("icon", "自定义图标")

        override fun install() = Unit
    }

    @Test
    fun `每个 xxxOption 声明都会出现在 settings 里`() {
        val settings = Probe().settings

        assertEquals(
            listOf(
                "probe_feature.type",
                "probe_feature.options",
                "probe_feature.text",
                "probe_feature.scale",
                "probe_feature.flag",
                "probe_feature.icon",
            ),
            settings.map { it.key },
            "声明即注册失效 —— 设置界面只会剩下功能开关",
        )
    }

    @Test
    fun `settings 的声明类型与投影类型一致`() {
        val byKey = Probe().settings.associateBy { it.key }

        assertEquals("IntSetting", byKey.getValue("probe_feature.type")::class.simpleName)
        assertEquals("MultiIntSetting", byKey.getValue("probe_feature.options")::class.simpleName)
        assertEquals("StringSetting", byKey.getValue("probe_feature.text")::class.simpleName)
        assertEquals("IntSliderSetting", byKey.getValue("probe_feature.scale")::class.simpleName)
        assertEquals("BooleanSetting", byKey.getValue("probe_feature.flag")::class.simpleName)
        val icon = byKey.getValue("probe_feature.icon") as StringSetting
        assertEquals(true, icon.isPngIcon)
        assertEquals("", icon.defaultValue)
        assertEquals(false, (byKey.getValue("probe_feature.text") as StringSetting).isPngIcon)
    }

    @Test
    fun `isHide 会透传到投影后的 Setting`() {
        val byKey = Probe().settings.associateBy { it.key }
        assertEquals(false, byKey.getValue("probe_feature.type").isHide)
    }
}
