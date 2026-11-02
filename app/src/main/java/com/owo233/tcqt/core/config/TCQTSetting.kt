package com.owo233.tcqt.core.config

import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KProperty

/**
 * 模块设置中心：所有配置项的 FastKV 持久化与读写。
 *
 * Hook 常用入口是 [getBoolean] / [getInt] / [getString] 与 [setBoolean] / [setInt] / [setString]。
 * 功能开发者不要直接实例化 [Setting]：在
 * [com.owo233.tcqt.core.action.ActionSpec.settings] 声明后，由
 * [SettingsRegistry.registerAllInto] 包装并注册进 [settingMap]。
 */
internal object TCQTSetting {

    private val config: SettingsStore by lazy {
        val path = "${HookEnv.moduleDataPath}/global/setting"
        SettingsStore(File(path), TCQTBuild.APP_NAME)
    }

    /**
     * 已注册配置项表：key -> 底层存储包装 [Setting]。
     *
     * lazy 首次访问时由 [ThemeSettings.registerSettings] 与
     * [SettingsRegistry.registerAllInto] 填充，覆盖主题配置和所有 ActionSpec 的 `settings` 声明。
     */
    private val registered: ConcurrentHashMap<String, Setting<out Any>> by lazy {
        val map = hashMapOf<String, Setting<out Any>>()
        ThemeSettings.registerSettings(map)
        ConcurrentHashMap(map)
    }

    private val allRegistered by lazy {
        SettingsRegistry.registerAllInto(registered)
        true
    }

    /** A complete stable snapshot for settings UI and backup; startup uses registeredSetting. */
    val settingMap: Map<String, Setting<out Any>>
        get() {
            check(allRegistered)
            return registered.toMap()
        }

    fun registerAction(action: ActionSpec) = SettingsRegistry.registerInto(action, registered)

    fun registeredSetting(key: String): Setting<out Any>? = registered[key]

    /** Validate the complete edit set before one cross-process commit. */
    fun applyEdits(values: Map<String, Any>) {
        values.forEach { (key, value) ->
            val setting = requireNotNull(registeredSetting(key)) { "Unknown setting: $key" }
            val valid = when (setting.type) {
                SettingType.BOOLEAN -> value is Boolean
                SettingType.INT, SettingType.INT_MULTI -> value is Int
                SettingType.STRING -> value is String
            }
            require(valid) { "Wrong setting type: $key" }
        }
        config.update { it.putAll(values) }
    }

    /** 清空全部已保存的配置。 */
    fun clearAll() {
        config.clear()
    }

    internal fun restore(plan: ConfigRestorePlan) = plan.applyTo(config)

    /** 指定 key 是否已有持久化值。 */
    fun containsKey(key: String): Boolean {
        return config.contains(key)
    }

    /** 全部已持久化的 key。 */
    fun getAllKeys(): MutableSet<String> {
        return config.all().keys.toMutableSet()
    }

    /**
     * 按原始字符串读取（不做类型检查与 trim），key 不存在时返回 [def]。
     */
    fun getRawString(key: String, def: String = ""): String {
        return config[key] as? String ?: def
    }

    /** 以原始字符串形式写入，跳过类型检查。 */
    fun putRawString(key: String, value: String) {
        config.put(key, value)
    }

    /** 删除指定 key 的持久化值。 */
    fun remove(key: String) {
        config.remove(key)
    }

    /**
     * 按泛型类型读取：优先走 [settingMap] 的注册项，未注册时按 `__type__` 类型标记读取。
     *
     * 支持 Boolean / Int / String；INT_MULTI 与 INT 互相兼容，类型不匹配时记错误日志并返回 null。
     */
    inline fun <reified T : Any> getValue(key: String): T? {
        return runCatching {
            val setting = registeredSetting(key)
            if (setting != null) {
                val requestedType = inferSettingType<T>()
                val isCompatible = setting.type == requestedType ||
                        (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                        (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return null
                }
                @Suppress("UNCHECKED_CAST")
                return (setting as Setting<T>).getValue()
            }

            val storedType = getStoredType(key)
            if (storedType != null) {
                val requestedType = inferSettingType<T>()
                if (storedType != requestedType) {
                    Log.e("Type mismatch for key: $key, stored: $storedType, requested: $requestedType")
                    return null
                }
                return readFromStorageByType<T>(key, storedType)
            }

            // Old FastKV values need not have a __type__ marker.
            config[key] as? T
        }.onFailure {
            Log.e("Failed to get value for key: $key", it)
        }.getOrNull()
    }

    /**
     * 按泛型类型写入：优先走 [settingMap] 的注册项，未注册时按类型标记写入存储。
     *
     * 支持 Boolean / Int / String；类型不匹配时记错误日志并丢弃写入。
     */
    inline fun <reified T : Any> setValue(key: String, value: T) {
        runCatching {
            val setting = registeredSetting(key)
            if (setting != null) {
                val requestedType = inferSettingType<T>()
                val isCompatible = setting.type == requestedType ||
                        (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                        (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return
                }
                @Suppress("UNCHECKED_CAST")
                (setting as Setting<T>).setValue(value)
                return
            }

            val type = inferSettingType<T>()
            saveStoredType(key, type)
            writeToStorage(key, value)
        }.onFailure {
            Log.e("Failed to set value for key: $key", it)
        }
    }

    private fun getStoredType(key: String): SettingType? {
        val typeKey = "__type__$key"
        val typeString = config[typeKey] as? String ?: return null
        return when (typeString) {
            "BOOLEAN" -> SettingType.BOOLEAN
            "INT" -> SettingType.INT
            "INT_MULTI" -> SettingType.INT_MULTI
            "STRING" -> SettingType.STRING
            else -> null
        }
    }

    private fun saveStoredType(key: String, type: SettingType) {
        val typeKey = "__type__$key"
        config.put(typeKey, type.name)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> readFromStorageByType(key: String, type: SettingType): T? {
        return when (type) {
            SettingType.BOOLEAN -> (config[key] as? Boolean ?: false) as T
            SettingType.INT, SettingType.INT_MULTI -> (config[key] as? Int ?: 0) as T
            SettingType.STRING -> (config[key] as? String ?: "") as T
        }
    }

    private inline fun <reified T : Any> writeToStorage(key: String, value: T) {
        when (T::class) {
            Boolean::class -> config.put(key, value as Boolean)
            Int::class -> config.put(key, value as Int)
            String::class -> config.put(key, value.toString())
            else -> Log.e("Unsupported type for key: $key, type: ${T::class}")
        }
    }

    private inline fun <reified T : Any> inferSettingType(): SettingType =
        when (T::class) {
            Boolean::class -> SettingType.BOOLEAN
            Int::class -> SettingType.INT
            String::class -> SettingType.STRING
            else -> throw IllegalArgumentException("Unsupported setting type: ${T::class}")
        }

    /**
     * 配置项存储类型，决定 [Setting] 读写走 FastKV 的哪个 API：
     * [BOOLEAN] / [INT] / [STRING] 分别对应 FastKV 的 Boolean / Int / String；
     * [INT_MULTI] 是多选位掩码，同样以 Int 存储。
     */
    enum class SettingType {
        BOOLEAN, INT, STRING, INT_MULTI
    }

    /**
     * 配置项的底层存储包装（内部使用，功能代码不要直接构造）。
     *
     * 每个实例对应一个 key，读写逻辑由 [type] 决定，缺失或解析失败时回退到 [default]；
     * 实例由 [ThemeSettings.registerSettings] / [SettingsRegistry.registerAllInto] 自动创建并放入 [settingMap]。
     * 功能侧声明配置用 `com.owo233.tcqt.core.config.Setting` 及其子类。
     *
     * 实现了 Kotlin 属性委托运算符，可直接 `by` 委托。
     *
     * @param key 存储键，必须与 [settingMap] 中注册的 key 一致
     */
    class Setting<T : Any>(
        val key: String,
        val type: SettingType,
        val default: T? = null
    ) {

        @Suppress("UNCHECKED_CAST")
        fun getValue(): T {
            return when (type) {
                SettingType.BOOLEAN -> config[key] as? Boolean ?: default as? Boolean ?: false
                SettingType.INT, SettingType.INT_MULTI -> config[key] as? Int ?: default as? Int ?: 0
                SettingType.STRING -> config[key] as? String ?: default as? String ?: ""
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        fun setValue(value: T) {
            when (type) {
                SettingType.BOOLEAN -> config.put(
                    key,
                    value as? Boolean ?: runCatching { value.toString().toBooleanStrict() }
                        .getOrDefault(false)
                )

                SettingType.INT, SettingType.INT_MULTI -> config.put(
                    key,
                    value as? Int ?: runCatching { value.toString().toInt() }
                        .getOrDefault(0)
                )

                SettingType.STRING -> config.put(key, value.toString())
            }
        }

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
            return getValue()
        }

        operator fun setValue(thisRef: Any, property: KProperty<*>?, value: T) {
            setValue(value)
        }
    }

    /** 读取字符串配置（trim 后），未配置时返回空串。 */
    fun getString(settingKey: String): String = getValue<String>(settingKey).orEmpty().trim()

    /** 读取 Int 配置（单选值或多选位掩码），未配置时返回 0。 */
    fun getInt(settingKey: String): Int = getValue<Int>(settingKey) ?: 0

    /** 读取布尔开关配置，未配置时返回 false。 */
    fun getBoolean(settingKey: String): Boolean = getValue<Boolean>(settingKey) ?: false

    /** 写入字符串配置。 */
    fun setString(settingKey: String, value: String) = setValue(settingKey, value)

    /** 写入 Int 配置（单选值或多选位掩码）。 */
    fun setInt(settingKey: String, value: Int) = setValue(settingKey, value)

    /** 写入布尔开关配置。 */
    fun setBoolean(settingKey: String, value: Boolean) = setValue(settingKey, value)
}
