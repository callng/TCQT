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
    val type: SettingType = SettingType.BOOLEAN,

    /**
     * 默认值
     * 如果为空，则根据类型自动设定：
     * - BOOLEAN: "false"
     * - INT: "0"
     * - STRING: ""
     */
    val defaultValue: String = ""
)

enum class SettingType {
    BOOLEAN, INT, STRING
}
