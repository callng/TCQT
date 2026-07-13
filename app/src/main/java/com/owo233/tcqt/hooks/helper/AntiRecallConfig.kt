package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.internals.setting.TCQTSetting

internal object AntiRecallConfig {

    const val SETTING_KEY = "msg_anti_recall.type"
    const val NEW_PARSER_INDEX = 0
    const val GRAY_TIP_INDEX = 1
    const val TOP_TIP_INDEX = 2
    const val DEFAULT_OPTIONS = 2

    private const val LEGACY_OPTIONS_MIGRATION_KEY =
        "msg_anti_recall.type.reminder_options_migrated"

    fun useNewParser(): Boolean = options().isFlagEnabled(NEW_PARSER_INDEX)

    fun isGrayTipEnabled(): Boolean = options().isFlagEnabled(GRAY_TIP_INDEX)

    fun isTopTipEnabled(): Boolean = options().isFlagEnabled(TOP_TIP_INDEX)

    fun hasEnabledReminder(): Boolean = isGrayTipEnabled() || isTopTipEnabled()

    @Synchronized
    fun migrateLegacyOptions() {
        if (TCQTSetting.getRawString(LEGACY_OPTIONS_MIGRATION_KEY) == "1") return

        if (TCQTSetting.containsKey(SETTING_KEY)) {
            val legacyOptions = options()
            val reminderMask = (1 shl GRAY_TIP_INDEX) or (1 shl TOP_TIP_INDEX)
            if ((legacyOptions and reminderMask) == 0 && legacyOptions in 0..1) {
                TCQTSetting.setInt(
                    SETTING_KEY,
                    legacyOptions or (1 shl GRAY_TIP_INDEX)
                )
            }
        }

        TCQTSetting.putRawString(LEGACY_OPTIONS_MIGRATION_KEY, "1")
    }

    private fun options(): Int = TCQTSetting.getInt(SETTING_KEY)
}
