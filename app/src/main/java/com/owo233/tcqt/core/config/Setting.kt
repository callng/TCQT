package com.owo233.tcqt.core.config

/**
 * 功能配置项的公共声明基类（sealed，不可直接实例化）。
 *
 * 功能开发引用的是 `com.owo233.tcqt.ext` 下的五个子类；[TCQTSetting.Setting] 不是它们，
 * 而是内部 FastKV 持久化包装类，由 [com.owo233.tcqt.core.action.ActionRegistry] 依
 * [ActionSpec.settings] 自动生成，功能代码不要直接构造。
 *
 * 在 [ActionSpec.settings] 声明（key 全局唯一）→ [ActionRegistry] 注册进
 * [TCQTSetting.settingMap] → 设置界面渲染，Hook 用 `TCQTSetting.getXxx(key)` 读取。
 * 功能开关来自 [ActionSpec.key]，与 `settings` 无关。
 *
 * 除 [BooleanSetting]（仅持久化，不渲染组件）外均可声明多个并自动渲染；
 * [isHide] 为 true 时不渲染，但仍注册进 [TCQTSetting.settingMap]，读写不受影响。
 */
sealed class Setting<T : Any> {

    /**
     * 唯一键名，同时是 FastKV 存储键，建议 `"<功能key>.<语义名>"`。
     *
     * 一经发布不要再改，否则用户已保存的配置会读不到。
     */
    abstract val key: String

    /** 配置项名称，设置界面标题。 */
    abstract val name: String

    /** 默认值；用户未修改过或持久化值缺失时生效，是 FastKV 读取的兜底。 */
    abstract val defaultValue: T

    /** 静态说明文字，显示在标题下方；也可改用 [ActionSpec.getSettingDesc] 返回动态描述。 */
    abstract val desc: String

    /**
     * 是否在设置界面隐藏该组件，默认 false。
     *
     * 为 true 时不渲染，但仍注册进 [TCQTSetting.settingMap]，功能代码可正常读写其值。
     */
    abstract val isHide: Boolean

    /**
     * 读取当前持久化值；该 key 未注册时回退到 [defaultValue]。
     *
     * 等价于 `TCQTSetting.getXxx(key)`。
     */
    @Suppress("UNCHECKED_CAST")
    fun getValue(): T {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        return s?.getValue() ?: defaultValue
    }

    /**
     * 写入持久化值；仅当该 key 已注册进 [TCQTSetting.settingMap] 时才真正写入，否则静默忽略。
     *
     * 需要写入任意 key 时改用 [TCQTSetting.setValue]。
     */
    @Suppress("UNCHECKED_CAST")
    fun setValue(value: T) {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        s?.setValue(value)
    }
}

/**
 * 布尔配置项：声明一个带元数据的持久化布尔值，**不渲染为设置界面开关**。
 *
 * 设置界面上的功能开关来自 [ActionSpec.key]（[com.owo233.tcqt.core.action.ActionRegistry]
 * 为每个 SWITCH 类型 Action 自动注册的布尔配置），与本类无关；
 * 本类的值由 Hook 用 `TCQTSetting.getBoolean/setBoolean(key, ...)` 自行读写。
 *
 * @param isHide 预留参数：本类不渲染界面组件，仅为统一 API 保留
 */
class BooleanSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Boolean = false,
    override val desc: String = "",
    override val isHide: Boolean = false
) : Setting<Boolean>()

/**
 * 字符串配置项，默认渲染为多行文本输入框。
 *
 * 典型用途：GUID、设备信息、自定义文本等。读取用 `TCQTSetting.getString(key)`（内部 trim，未配置返回空串）。
 *
 * @param placeholder 输入框占位提示文案；为空时界面自动使用“填写{name}内容”
 * @param hasTextAreas 预留字段，保持默认 false
 * @param isPngIcon 使用 PNG 选择器，值为内嵌 PNG 的 Base64，空值使用默认图标
 */
class StringSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: String = "",
    override val desc: String = "",
    val placeholder: String = "",
    val hasTextAreas: Boolean = false,
    override val isHide: Boolean = false,
    val isPngIcon: Boolean = false,
) : Setting<String>()

/**
 * 单选配置项，渲染为一组 RadioButton。
 *
 * 存储值为 1-based：[options] 中第 i 个选项对应 `i + 1`，0 表示“未选择/默认”。
 * 用 `TCQTSetting.getInt(key)` 读回后自行映射到 [options]。
 */
class IntSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Int = 0,
    override val desc: String = "",
    val options: List<String>,
    override val isHide: Boolean = false
) : Setting<Int>()

/**
 * 多选配置项，渲染为一组 Checkbox。
 *
 * 存储值为位掩码：[options] 中第 i 个选项对应第 i 位（`1 shl i`）。
 * 用 `TCQTSetting.getInt(key)` 读回掩码，再以 [isFlagEnabled] 判断某一项是否选中。
 *
 * @param forcedSelections 界面勾选联动：key 为被选中项的下标，value 为随之强制选中的下标列表，
 *   例如 `mapOf(0 to listOf(2))`；只影响界面勾选，不改变其它位在存储值中的含义
 */
class MultiIntSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Int = 0,
    override val desc: String = "",
    val options: List<String>,
    val forcedSelections: Map<Int, List<Int>> = emptyMap(),
    override val isHide: Boolean = false
) : Setting<Int>()

/**
 * 滑块配置项，渲染为一个带数值标签的 Slider。
 *
 * 存储值为 [min]..[max] 内的整数，[step] 大于 1 时按步进生成吸附点；
 * 读用 `TCQTSetting.getInt(key)`，写用 `TCQTSetting.setValue(key, value)`。
 */
class IntSliderSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Int = 0,
    override val desc: String = "",
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val suffix: String = "",
    override val isHide: Boolean = false
) : Setting<Int>()
