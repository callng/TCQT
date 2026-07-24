package com.owo233.tcqt.internals.setting

internal enum class ModuleThemeMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStoredValue(value: String): ModuleThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

internal object ThemeSettings {
    const val KEY_THEME_MODE = "tcqt_ui.theme_mode"
    const val KEY_MONET_ENABLED = "tcqt_ui.monet_enabled"

    val themeMode: ModuleThemeMode
        get() = ModuleThemeMode.fromStoredValue(TCQTSetting.getString(KEY_THEME_MODE))

    var monetEnabled: Boolean
        get() = TCQTSetting.getBoolean(KEY_MONET_ENABLED)
        set(value) = TCQTSetting.setBoolean(KEY_MONET_ENABLED, value)

    fun setThemeMode(mode: ModuleThemeMode) {
        TCQTSetting.setString(KEY_THEME_MODE, mode.storedValue)
    }

    fun registerSettings(map: HashMap<String, TCQTSetting.Setting<out Any>>) {
        map[KEY_THEME_MODE] = TCQTSetting.Setting(
            key = KEY_THEME_MODE,
            type = TCQTSetting.SettingType.STRING,
            default = ModuleThemeMode.System.storedValue,
        )
        map[KEY_MONET_ENABLED] = TCQTSetting.Setting(
            key = KEY_MONET_ENABLED,
            type = TCQTSetting.SettingType.BOOLEAN,
            default = false,
        )
    }
}
