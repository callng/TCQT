package com.owo233.tcqt.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterAction(
    /**
     * 是否启用，默认为true
     */
    val enabled: Boolean = true
)

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterSetting(
    /**
     * 配置项键名
     */
    val key: String,

    /**
     * 配置项显示名称
     */
    val name: String,

    /**
     * 配置项类型
     */
    val type: SettingType,

    /**
     * 默认值
     * 如果为空，则根据类型自动设定：
     * - BOOLEAN: "false"
     * - INT: "0"
     * - STRING: ""
     */
    val defaultValue: String = "",

    /**
     * 配置项描述
     */
    val desc: String = "",

    /**
     * 是否包含多行文本框
     */
    val hasTextAreas: Boolean = false,

    /**
     * 界面显示顺序，数值越小越靠前
     */
    val uiOrder: Int = 1000,

    /**
     * 文本框占位符（当hasTextAreas为true时使用）
     */
    val textAreaPlaceholder: String = "",

    /**
     * 是否在前端UI中隐藏此配置项
     */
    val hidden: Boolean = false,

    /**
     * 选项列表（仅当type为INT且需要单选/下拉时使用）
     * 格式: "选项1|选项2|选项3"
     * 选项的索引值(0,1,2...)将作为INT值保存
     */
    val options: String = "",

    /**
     * UI分组Tab名称
     * 默认为"基础"，可自定义Tab名称实现功能分组
     */
    val uiTab: String = ""
)

enum class SettingType {
    BOOLEAN, INT, STRING
}
