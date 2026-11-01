package com.owo233.tcqt.core.config

import com.owo233.tcqt.core.action.ActionRegistry
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.action.ActionSpec

/**
 * 配置注册：把每个 [com.owo233.tcqt.core.action.ActionSpec] 声明的配置项
 * 展开成 `TCQTSetting` 的底层存储包装。
 *
 * 由 `TCQTSetting.settingMap` 的 lazy 初始化回调，依赖方向必须保持单向的
 * `config → action`，否则会形成隐式双向依赖。
 */
internal object SettingsRegistry {

    /**
     * 把全部 Action 的配置项注册进 [target]。
     *
     * `uiType == SWITCH` 且 key 非空时先把功能开关本身注册为 BOOLEAN，随后按声明类型注册每个 `settings` 项。
     */
    fun registerAllInto(target: MutableMap<String, TCQTSetting.Setting<out Any>>) {
        ActionRegistry.allActionClasses().forEach { actionClass ->
            val action = ActionRegistry.instanceOf(actionClass) ?: return@forEach
            registerInto(action, target)
        }
    }

    fun registerInto(action: ActionSpec, target: MutableMap<String, TCQTSetting.Setting<out Any>>) {
        if (action.key.isNotBlank() && action.uiType == ActionUiType.SWITCH) {
            target[action.key] = TCQTSetting.Setting(
                action.key,
                TCQTSetting.SettingType.BOOLEAN,
                action.defaultEnabled
            )
        }
        action.settings.forEach { s ->
            val type = when (s) {
                is BooleanSetting -> TCQTSetting.SettingType.BOOLEAN
                is StringSetting -> TCQTSetting.SettingType.STRING
                is IntSetting -> TCQTSetting.SettingType.INT
                is IntSliderSetting -> TCQTSetting.SettingType.INT
                is MultiIntSetting -> TCQTSetting.SettingType.INT_MULTI
            }
            target[s.key] = TCQTSetting.Setting(
                s.key,
                type,
                s.defaultValue
            )
        }
    }
}
