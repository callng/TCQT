package com.owo233.tcqt.api

import com.owo233.tcqt.core.config.BooleanSetting
import com.owo233.tcqt.core.config.IntSetting
import com.owo233.tcqt.core.config.IntSliderSetting
import com.owo233.tcqt.core.config.MultiIntSetting
import com.owo233.tcqt.core.config.Setting
import com.owo233.tcqt.core.config.StringSetting
import com.owo233.tcqt.core.config.TCQTSetting
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 一个功能配置项：**声明即唯一定义**。
 *
 * 持久化 key 由 [key] 从 `featureKey` 与 `settingKey` **派生**，声明处与读取处
 * 不可能写出两个不同的字符串。
 *
 * `com.owo233.tcqt.core.config.Setting` 是 `sealed class`，其直接子类必须同包同模块，
 * 因此这里改用**投影**：通过 [toSetting] 产出既有 `Setting` 子类，使
 * `SettingsRegistry` / `FeatureCatalog` 无需任何改动。
 *
 * ```kotlin
 * object FakePicSize : Feature(key = "fake_pic_size", name = "篡改图片显示大小") {
 *     private val type by intOption("type", "图片比例", defaultValue = 1,
 *         options = listOf("默认", "最小", "略小", "略大", "最大", "自定义"))
 *
 *     override fun install() {
 *         val mode = type                // ← 读取就是属性，没有字符串
 *         type = 2                       // ← 写入需声明为 var
 *     }
 * }
 * ```
 *
 * 每个子类都带 [isHide]：为 true 时 `FeatureCatalog` 会跳过渲染（但仍会注册与持久化）。
 * 漏写时该配置项会出现在设置界面里，编译期与单元测试都发现不了。
 */
sealed class Option<T : Any> : ReadWriteProperty<Any?, T> {

    /** 所属功能的 key。 */
    abstract val featureKey: String

    /** 配置项在功能内的短名。 */
    abstract val settingKey: String

    /** 设置界面显示的标题。 */
    abstract val name: String

    /** 未存储时的默认值。 */
    abstract val defaultValue: T

    /** 设置界面显示的说明。 */
    abstract val desc: String

    /** 为 true 时不在设置界面渲染，但仍会注册与持久化。 */
    abstract val isHide: Boolean

    /**
     * 完整持久化 key，派生规则唯一：`"$featureKey.$settingKey"`。
     *
     * 一经发布不可改动，否则用户已保存的配置会读不到。
     */
    val key: String get() = "$featureKey.$settingKey"

    /** 当前持久化值。 */
    abstract var value: T

    /** 写入持久化值。 */
    abstract fun set(value: T)

    /**
     * 该配置项在存储中**是否已被显式写过** —— 区分「用户从未配置过」与
     * 「用户配置成了默认值」。
     */
    fun isSet(): Boolean = TCQTSetting.containsKey(key)

    /** 投影成既有 [Setting] 子类，供注册表与设置界面消费。 */
    internal abstract fun toSetting(): Setting<T>

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}

/** 布尔配置项：只持久化，不渲染界面组件。 */
class BooleanOption internal constructor(
    override val featureKey: String,
    override val settingKey: String,
    override val name: String,
    override val defaultValue: Boolean,
    override val desc: String,
    override val isHide: Boolean = false,
) : Option<Boolean>() {

    override var value: Boolean
        get() = TCQTSetting.getBoolean(key)
        set(v) = TCQTSetting.setBoolean(key, v)

    override fun set(value: Boolean) = TCQTSetting.setBoolean(key, value)

    override fun toSetting(): Setting<Boolean> =
        BooleanSetting(key, name, defaultValue, desc, isHide)
}

/** 单选配置项：渲染为 RadioButton 组，存储值为 `下标 + 1`。 */
class IntOption internal constructor(
    override val featureKey: String,
    override val settingKey: String,
    override val name: String,
    override val defaultValue: Int,
    override val desc: String,
    val options: List<String>,
    override val isHide: Boolean = false,
) : Option<Int>() {

    override var value: Int
        get() = TCQTSetting.getInt(key)
        set(v) = TCQTSetting.setInt(key, v)

    override fun set(value: Int) = TCQTSetting.setInt(key, value)

    override fun toSetting(): Setting<Int> =
        IntSetting(key, name, defaultValue, desc, options, isHide)
}

/** 多选配置项：渲染为 Checkbox 组，存储值为位掩码。 */
class MultiIntOption internal constructor(
    override val featureKey: String,
    override val settingKey: String,
    override val name: String,
    override val defaultValue: Int,
    override val desc: String,
    val options: List<String>,
    val forcedSelections: Map<Int, List<Int>> = emptyMap(),
    override val isHide: Boolean = false,
) : Option<Int>() {

    override var value: Int
        get() = TCQTSetting.getInt(key)
        set(v) = TCQTSetting.setInt(key, v)

    override fun set(value: Int) = TCQTSetting.setInt(key, value)

    override fun toSetting(): Setting<Int> =
        MultiIntSetting(key, name, defaultValue, desc, options, forcedSelections, isHide)
}

/** 滑块配置项：存储 `min..max` 内的整数。 */
class SliderOption internal constructor(
    override val featureKey: String,
    override val settingKey: String,
    override val name: String,
    override val defaultValue: Int,
    override val desc: String,
    val min: Int,
    val max: Int,
    val step: Int,
    val suffix: String,
    override val isHide: Boolean = false,
) : Option<Int>() {

    override var value: Int
        get() = TCQTSetting.getInt(key)
        set(v) = TCQTSetting.setInt(key, v)

    override fun set(value: Int) = TCQTSetting.setInt(key, value)

    override fun toSetting(): Setting<Int> =
        IntSliderSetting(key, name, defaultValue, desc, min, max, step, suffix, isHide)
}

/** 字符串配置项：文本输入框或 PNG 图标选择器。 */
class StringOption internal constructor(
    override val featureKey: String,
    override val settingKey: String,
    override val name: String,
    override val defaultValue: String,
    override val desc: String,
    val placeholder: String,
    override val isHide: Boolean = false,
    val isPngIcon: Boolean = false,
) : Option<String>() {

    override var value: String
        get() = TCQTSetting.getString(key)
        set(v) = TCQTSetting.setString(key, v)

    override fun set(value: String) = TCQTSetting.setString(key, value)

    override fun toSetting(): Setting<String> =
        StringSetting(key, name, defaultValue, desc, placeholder, isHide = isHide, isPngIcon = isPngIcon)
}
