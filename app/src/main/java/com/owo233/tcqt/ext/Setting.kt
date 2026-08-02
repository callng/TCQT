package com.owo233.tcqt.ext

import com.owo233.tcqt.internals.setting.TCQTSetting

/**
 * 功能配置项的公共声明基类（sealed，不可直接实例化）。
 *
 * 功能开发时引用的是本包（[com.owo233.tcqt.ext]）下的四个子类：
 * [BooleanSetting]、[StringSetting]、[IntSetting]、[MultiIntSetting]。
 * 注意它们并不在 `TCQTSetting.Setting` 里 —— 后者是模块内部负责 FastKV
 * 持久化的底层包装类，由 [com.owo233.tcqt.ActionManager] 在初始化时根据各
 * 功能的 [IAction.settings] 自动生成，功能代码不需要也不应该直接构造它。
 *
 * 使用流程：
 * 1. 在 [IAction] 的 `settings` 属性中声明一个或多个配置项，key 全局唯一；
 * 2. 模块启动后 [com.owo233.tcqt.ActionManager] 会把它们注册进
 *    [TCQTSetting.settingMap]；其中文本框、单选、多选会由设置界面自动渲染，
 *    而功能本身的开关来自 [IAction.key]（与 `settings` 无关）；
 * 3. Hook 代码运行时通过 `TCQTSetting.getBoolean/getInt/getString(key)`
 *    读取持久化后的值。
 *
 * 约定：一个功能最多声明一个选项组（[IntSetting] 或 [MultiIntSetting]），
 * 设置界面只取 `settings` 中第一个选项组渲染；[StringSetting] 可以声明多个，
 * 会全部渲染为多行文本输入框；[BooleanSetting] 只作为持久化的布尔配置项，
 * 不会渲染成界面组件。将 [isHide] 置为 true 的组件不会在设置界面渲染，
 * 但仍会注册进 [TCQTSetting.settingMap]，Hook 读写不受影响。
 */
sealed class Setting<T : Any> {

    /**
     * 配置项唯一键名，同时也是 FastKV 持久化使用的存储键。
     *
     * 建议格式：`"<功能key>.<语义名>"`，例如 `"fake_pic_size.type"`。
     * 一经发布就不要再改动，否则用户已保存的配置会读不到。
     */
    abstract val key: String

    /**
     * 配置项名称，显示在设置界面上的标题。
     */
    abstract val name: String

    /**
     * 默认值：用户未修改过、或持久化值缺失时生效。注册进
     * [TCQTSetting.settingMap] 后，读操作会以它作为 FastKV 读取的兜底。
     */
    abstract val defaultValue: T

    /**
     * 配置项的静态说明文字，显示在设置界面标题下方。也可以留空，改用
     * [IAction.getSettingDesc] 按 key 返回动态描述。
     */
    abstract val desc: String

    /**
     * 是否在设置界面隐藏该组件。
     *
     * 为 true 时设置界面不再渲染该配置项（文本框 / 选项组），但配置项仍会
     * 注册进 [TCQTSetting.settingMap]，功能代码依然可以正常读写其值。
     * 默认 false，即正常显示。
     */
    abstract val isHide: Boolean

    /**
     * 读取当前持久化值；该 key 未注册时回退到 [defaultValue]。
     *
     * Hook 中通常直接使用 `TCQTSetting.getXxx(key)`，二者等价。
     */
    @Suppress("UNCHECKED_CAST")
    fun getValue(): T {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        return s?.getValue() ?: defaultValue
    }

    /**
     * 写入持久化值。
     *
     * 注意：仅当该 key 已注册进 [TCQTSetting.settingMap]（即被某个
     * [IAction.settings] 声明过）时才会真正写入，否则静默不生效。
     * Hook 内需要写入任意 key 时，请改用 [TCQTSetting.setValue]。
     */
    @Suppress("UNCHECKED_CAST")
    fun setValue(value: T) {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        s?.setValue(value)
    }
}

/**
 * 布尔配置项：声明一个可持久化的布尔值。
 *
 * 注意：它**不会**在模块设置界面渲染成开关组件。设置界面上的功能开关来自
 * [IAction.key] 本身（[com.owo233.tcqt.ActionManager] 为每个 SWITCH 类型
 * Action 自动注册的布尔配置），与 [BooleanSetting] 无关。
 *
 * [BooleanSetting] 的实际作用是声明一个带元数据（key/name/desc）的布尔存储项，
 * 注册进 [TCQTSetting.settingMap] 后由 Hook 代码读写，例如 ChangeGuid 里的
 * “是否启用更改”只是登录弹窗内部开关的状态，模块设置界面并不展示它：
 * ```
 * // 声明（放在 [IAction.settings] 里）
 * BooleanSetting("change_guid.boolean.isEnabled", "是否启用更改")
 * ```
 * ```
 * // Hook 内读写
 * val enabled = TCQTSetting.getBoolean("change_guid.boolean.isEnabled")
 * TCQTSetting.setBoolean("change_guid.boolean.isEnabled", true)
 * ```
 *
 * @param key 唯一键名，见 [Setting.key]
 * @param name 配置项名称（元数据，当前界面不展示）
 * @param defaultValue 默认布尔值，未存储时读取返回该值，默认 false
 * @param desc 说明文字（元数据，当前界面不展示）
 * @param isHide 预留参数：该配置项本身不渲染界面组件，保留仅为统一 API，
 *   默认 false
 */
class BooleanSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Boolean = false,
    override val desc: String = "",
    override val isHide: Boolean = false
) : Setting<Boolean>()

/**
 * 字符串配置项，在设置界面渲染为一个多行文本输入框。
 *
 * 典型用途：让用户填写自定义文本，如 GUID、设备信息、自定义图片尺寸等。
 *
 * 声明方式：
 * ```
 * StringSetting(
 *     key = "fake_pic_size.custom_width",
 *     name = "自定义宽度",
 *     defaultValue = "",
 *     desc = "图片比例选择自定义时生效",
 *     placeholder = "例如 500"
 * )
 * ```
 *
 * 读取方式：`TCQTSetting.getString(key)`（内部会 trim，未配置时返回空串）。
 *
 * @param key 唯一键名，见 [Setting.key]
 * @param name 设置界面显示的标题
 * @param defaultValue 默认文本，默认空串
 * @param desc 设置界面显示的说明文字
 * @param placeholder 输入框占位提示文案；为空时界面自动使用“填写{name}内容”
 * @param hasTextAreas 预留字段：当前设置界面未读取，所有 [StringSetting] 均按
 *   多行文本域渲染，建议保持默认值 false
 * @param isHide 为 true 时在设置界面隐藏该输入框，默认 false
 */
class StringSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: String = "",
    override val desc: String = "",
    val placeholder: String = "",
    val hasTextAreas: Boolean = false,
    override val isHide: Boolean = false
) : Setting<String>()

/**
 * 单选配置项，在设置界面渲染为一组 RadioButton，只能选一个。
 *
 * 存储值编码：[options] 中第 i 个选项对应存储值 `i + 1`（从 1 开始，不是下标），
 * 值为 0 表示“未选择/默认”。读取时用 `TCQTSetting.getInt(key)` 取回该
 * 1-based 值，再自行映射到 [options] 的选项。
 *
 * 声明方式：
 * ```
 * IntSetting(
 *     key = "switch_login_mode.type",
 *     name = "登录类型",
 *     defaultValue = 1,
 *     desc = "选择 QQ 登录时使用的模式",
 *     options = listOf("手机模式", "平板模式")
 * )
 * ```
 * 上面例子中“手机模式”存储值为 1，“平板模式”存储值为 2。
 *
 * 读取方式：
 * ```
 * when (TCQTSetting.getInt("switch_login_mode.type")) {
 *     1 -> // 手机模式
 *     2 -> // 平板模式
 * }
 * ```
 *
 * @param key 唯一键名，见 [Setting.key]
 * @param name 设置界面显示的标题
 * @param defaultValue 默认选中项的存储值，默认 0（未选择）
 * @param desc 设置界面显示的说明文字
 * @param options 选项标签列表，按顺序对应存储值 1、2、3……
 * @param isHide 为 true 时在设置界面隐藏该选项组，默认 false
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
 * 多选配置项，在设置界面渲染为一组 Checkbox，可多选。
 *
 * 存储值编码：一个 [Int] 位掩码，第 i 个选项对应第 i 位（`1 shl i`）。
 * 读取时用 `TCQTSetting.getInt(key)` 取回掩码，再用
 * [isFlagEnabled]（`value.isFlagEnabled(index)`）判断第 index 个选项是否选中。
 *
 * 声明方式：
 * ```
 * MultiIntSetting(
 *     key = "msg_anti_recall.options",
 *     name = "防撤回选项",
 *     defaultValue = 0,
 *     desc = "可多选",
 *     options = listOf("使用新版解析方式", "底部灰字提醒", "顶部撤回提醒")
 * )
 * ```
 *
 * 读取方式：
 * ```
 * val mask = TCQTSetting.getInt("msg_anti_recall.options")
 * if (mask.isFlagEnabled(0)) { /* 选中了“使用新版解析方式” */ }
 * ```
 *
 * @param key 唯一键名，见 [Setting.key]
 * @param name 设置界面显示的标题
 * @param defaultValue 默认选中的位掩码，默认 0（全不选）
 * @param desc 设置界面显示的说明文字
 * @param options 选项标签列表，按顺序对应位 0、1、2……
 * @param forcedSelections 选项联动：key 为被选中选项的下标，value 为随之
 *   强制选中的其它选项下标列表。例如选中下标 0 时强制勾选下标 2：
 *   `forcedSelections = mapOf(0 to listOf(2))`。只在设置界面做勾选联动，
 *   不会改变其它位在存储值中的含义
 * @param isHide 为 true 时在设置界面隐藏该选项组，默认 false
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
