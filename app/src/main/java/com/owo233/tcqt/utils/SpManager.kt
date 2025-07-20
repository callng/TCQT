package com.owo233.tcqt.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.owo233.tcqt.data.TCQTBuild

object SpManager {
    private const val SP_NAME = TCQTBuild.APP_NAME + "_sp"

    const val SP_KEY_GUID = "guid"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isInit(): Boolean {
        return ::sp.isInitialized
    }

    /**
     * 获取字符串值
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return sp.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * 设置字符串值
     */
    fun setString(key: String, value: String) {
        sp.edit { putString(key, value) }
    }

    /**
     * 获取布尔值
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sp.getBoolean(key, defaultValue)
    }

    /**
     * 设置布尔值
     */
    fun setBoolean(key: String, value: Boolean) {
        sp.edit { putBoolean(key, value) }
    }

    /**
     * 获取整数值
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return sp.getInt(key, defaultValue)
    }

    /**
     * 设置整数值
     */
    fun setInt(key: String, value: Int) {
        sp.edit { putInt(key, value) }
    }

    /**
     * 获取长整数值
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sp.getLong(key, defaultValue)
    }

    /**
     * 设置长整数值
     */
    fun setLong(key: String, value: Long) {
        sp.edit { putLong(key, value) }
    }

    /**
     * 获取浮点数值
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return sp.getFloat(key, defaultValue)
    }

    /**
     * 设置浮点数值
     */
    fun setFloat(key: String, value: Float) {
        sp.edit { putFloat(key, value) }
    }

    /**
     * 检查是否包含指定键
     */
    fun contains(key: String): Boolean {
        return sp.contains(key)
    }

    /**
     * 移除指定键的值
     */
    fun remove(key: String) {
        sp.edit { remove(key) }
    }

    /**
     * 清除所有数据
     */
    fun clear() {
        sp.edit { clear() }
    }
}
