package com.owo233.tcqt.ext

import com.owo233.tcqt.internals.setting.TCQTSetting

sealed class Setting<T : Any> {

    abstract val key: String
    abstract val name: String
    abstract val defaultValue: T
    abstract val desc: String

    @Suppress("UNCHECKED_CAST")
    fun getValue(): T {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        return s?.getValue() ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    fun setValue(value: T) {
        val s = TCQTSetting.settingMap[key] as? TCQTSetting.Setting<T>
        s?.setValue(value)
    }
}

class BooleanSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Boolean = false,
    override val desc: String = ""
) : Setting<Boolean>()

class StringSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: String = "",
    override val desc: String = "",
    val placeholder: String = "",
    val hasTextAreas: Boolean = false
) : Setting<String>()

class IntSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Int = 0,
    override val desc: String = "",
    val options: List<String>
) : Setting<Int>()

class MultiIntSetting(
    override val key: String,
    override val name: String,
    override val defaultValue: Int = 0,
    override val desc: String = "",
    val options: List<String>,
    val forcedSelections: Map<Int, List<Int>> = emptyMap()
) : Setting<Int>()
