package com.owo233.tcqt.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterAction()

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
     * 是否需要红色标记（表示有风险）
     */
    val isRedMark: Boolean = false,

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
    val textAreaPlaceholder: String = ""
)

enum class SettingType {
    BOOLEAN, INT, STRING
}
