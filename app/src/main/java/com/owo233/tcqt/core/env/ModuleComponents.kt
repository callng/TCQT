package com.owo233.tcqt.core.env

/**
 * 模块内部组件的类名字符串常量：功能侧按名字加载（`ClassLoader.loadClass` / `Intent.setClassName`），
 * 而不 import `ui` 层的类。这里只放字符串，类被搬走时必须同步更新。
 */
object ModuleComponents {

    /** 模块设置主界面（`ui/settings/SettingActivity.kt`）。 */
    const val SETTINGS_ACTIVITY: String = "com.owo233.tcqt.ui.settings.SettingActivity"

    /** 通知渠道管理页（`ui/settings/NotificationChannelManagerActivity.kt`）。 */
    const val NOTIFICATION_CHANNEL_ACTIVITY: String =
        "com.owo233.tcqt.ui.settings.NotificationChannelManagerActivity"

    /** 脚本管理页（`ui/settings/ScriptManagerActivity.kt`）。 */
    const val SCRIPT_MANAGER_ACTIVITY: String =
        "com.owo233.tcqt.ui.settings.ScriptManagerActivity"
}
