package com.owo233.tcqt.internals.setting

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.utils.log.Log
import io.fastkv.FastKV
import kotlin.reflect.KProperty

/**
 * 模块设置中心：统一负责所有配置项的 FastKV 持久化与读写。
 *
 * Hook 代码最常用的入口是本文件顶层的快捷函数：
 * [getBoolean]、[getInt]、[getString] 用于读取，
 * [setBoolean]、[setInt]、[setString] 用于写入。
 *
 * 功能开发者不需要直接实例化 [Setting]：各功能只需在
 * [com.owo233.tcqt.ext.IAction.settings] 里声明
 * [com.owo233.tcqt.ext.BooleanSetting] 等配置，
 * [com.owo233.tcqt.ActionManager.registerAllSettings] 会自动把它们包装成
 * 本文件的 [Setting] 并注册进 [settingMap]。
 */
internal object TCQTSetting {

    private val config: FastKV by lazy {
        val path = "${HookEnv.moduleDataPath}/global/setting"
        FastKV.Builder(path, TCQTBuild.APP_NAME).build()
    }

    /**
     * 已注册配置项表：key -> 底层存储包装 [Setting]。
     *
     * 由 [ThemeSettings.registerSettings] 与
     * [com.owo233.tcqt.ActionManager.registerAllSettings] 在首次访问时填充，
     * 覆盖主题配置和所有 [com.owo233.tcqt.ext.IAction] 的
     * `settings` 声明。
     */
    val settingMap: HashMap<String, Setting<out Any>> by lazy {
        val map = hashMapOf<String, Setting<out Any>>()
        ThemeSettings.registerSettings(map)
        com.owo233.tcqt.ActionManager.registerAllSettings(map)
        map
    }

    /**
     * 清空全部已保存的配置。
     */
    fun clearAll() {
        config.clear()
    }

    /**
     * 判断指定 key 是否已有持久化值。
     */
    fun containsKey(key: String): Boolean {
        return config.contains(key)
    }

    /**
     * 返回全部已持久化的 key。
     */
    fun getAllKeys(): MutableSet<String> {
        return config.all.keys
    }

    /**
     * 按原始字符串读取（不做类型检查与 trim），key 不存在时返回 [def]。
     */
    fun getRawString(key: String, def: String = ""): String {
        return config.getString(key, def) ?: ""
    }

    /**
     * 以原始字符串形式写入，跳过类型检查。
     */
    fun putRawString(key: String, value: String) {
        config.putString(key, value)
    }

    /**
     * 删除指定 key 的持久化值。
     */
    fun remove(key: String) {
        config.remove(key)
    }

    /**
     * 按泛型类型读取配置值，优先走 [settingMap] 中的注册项，未注册时回退到
     * 存储的类型标记直接读取。
     *
     * 支持的类型：Boolean / Int / String。其中 `INT_MULTI` 与 `INT` 视为
     * 兼容，可以互相以 Int 读取。类型不匹配时记录错误日志并返回 null。
     *
     * 功能代码一般直接使用 [getBoolean]、[getInt]、[getString]。
     */
    inline fun <reified T : Any> getValue(key: String): T? {
        return runCatching {
            val setting = settingMap[key]
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

            null
        }.onFailure {
            Log.e("Failed to get value for key: $key", it)
        }.getOrNull()
    }

    /**
     * 按泛型类型写入配置值，优先走 [settingMap] 中的注册项，未注册时按类型
     * 标记直接写入存储。
     *
     * 支持的类型：Boolean / Int / String。类型不匹配时记录错误日志并丢弃写入。
     * 功能代码一般直接使用 [setBoolean]、[setInt]、[setString]。
     */
    inline fun <reified T : Any> setValue(key: String, value: T) {
        runCatching {
            val setting = settingMap[key]
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
        val typeString = config.getString(typeKey, null) ?: return null
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
        config.putString(typeKey, type.name)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> readFromStorageByType(key: String, type: SettingType): T? {
        return when (type) {
            SettingType.BOOLEAN -> config.getBoolean(key, false) as T
            SettingType.INT, SettingType.INT_MULTI -> config.getInt(key, 0) as T
            SettingType.STRING -> (config.getString(key, null) ?: "") as T
        }
    }

    private inline fun <reified T : Any> writeToStorage(key: String, value: T) {
        when (T::class) {
            Boolean::class -> config.putBoolean(key, value as Boolean)
            Int::class -> config.putInt(key, value as Int)
            String::class -> config.putString(key, value.toString())
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
     * 配置项的存储类型，决定 [Setting] 读写走 FastKV 的哪个 API：
     * - [BOOLEAN]：布尔开关（FastKV Boolean）
     * - [INT]：单选配置，值为 Int（FastKV Int）
     * - [STRING]：字符串配置（FastKV String）
     * - [INT_MULTI]：多选配置，值为 Int 位掩码（FastKV Int）
     */
    enum class SettingType {
        BOOLEAN, INT, STRING, INT_MULTI
    }

    /**
     * 配置项的底层存储包装（内部使用，功能代码一般不要直接构造）。
     *
     * 每个实例对应一个 key，负责在 FastKV 中读写该 key 的持久化值，
     * 读写逻辑由 [type] 决定，缺失或解析失败时回退到 [default]。
     * 实例由 [ThemeSettings.registerSettings] 与
     * [com.owo233.tcqt.ActionManager.registerAllSettings] 在模块初始化时
     * 自动创建并放入 [settingMap]。
     *
     * 功能侧面向开发者的是 [com.owo233.tcqt.ext.Setting] 及其四个子类：
     * [com.owo233.tcqt.ext.BooleanSetting]、[com.owo233.tcqt.ext.StringSetting]、
     * [com.owo233.tcqt.ext.IntSetting]、[com.owo233.tcqt.ext.MultiIntSetting]。
     *
     * 除了 [getValue]/[setValue]，本类还实现了 Kotlin 属性委托运算符，
     * 可直接用于 `by` 委托：
     * ```
     * val myText by TCQTSetting.Setting(
     *     "my_feature.text",
     *     TCQTSetting.SettingType.STRING,
     *     ""
     * )
     * ```
     *
     * @param key 存储键，必须与 [settingMap] 中注册的 key 一致
     * @param type 存储类型，见 [SettingType]
     * @param default 未存储或解析失败时使用的默认值
     */
    class Setting<T : Any>(
        val key: String,
        val type: SettingType,
        val default: T? = null
    ) {

        @Suppress("UNCHECKED_CAST")
        fun getValue(): T {
            return when (type) {
                SettingType.BOOLEAN -> config.getBoolean(key, default as? Boolean ?: false)
                SettingType.INT, SettingType.INT_MULTI -> config.getInt(key, default as? Int ?: 0)
                SettingType.STRING -> config.getString(key, default as? String ?: "") ?: ""
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        fun setValue(value: T) {
            when (type) {
                SettingType.BOOLEAN -> config.putBoolean(
                    key,
                    value as? Boolean ?: runCatching { value.toString().toBooleanStrict() }
                        .getOrDefault(false)
                )

                SettingType.INT, SettingType.INT_MULTI -> config.putInt(
                    key,
                    value as? Int ?: runCatching { value.toString().toInt() }
                        .getOrDefault(0)
                )

                SettingType.STRING -> config.putString(key, value.toString())
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

    /**
     * 读取字符串配置：按 [SettingType.STRING] 读取并 trim，未配置时返回空串。
     */
    fun getString(settingKey: String): String = getValue<String>(settingKey).orEmpty().trim()

    /**
     * 读取 Int 配置（单选或多选位掩码），未配置时返回 0。
     */
    fun getInt(settingKey: String): Int = getValue<Int>(settingKey) ?: 0

    /**
     * 读取布尔开关配置，未配置时返回 false。
     */
    fun getBoolean(settingKey: String): Boolean = getValue<Boolean>(settingKey) ?: false

    /**
     * 写入字符串配置。
     */
    fun setString(settingKey: String, value: String) = setValue(settingKey, value)

    /**
     * 写入 Int 配置（单选值或多选位掩码）。
     */
    fun setInt(settingKey: String, value: Int) = setValue(settingKey, value)

    /**
     * 写入布尔开关配置。
     */
    fun setBoolean(settingKey: String, value: Boolean) = setValue(settingKey, value)
}
