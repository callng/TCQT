package com.owo233.tcqt.hooks.helper

import android.content.SharedPreferences

class MockSharedPreferences(
    private val original: SharedPreferences
) : SharedPreferences {

    override fun getLong(key: String?, defValue: Long): Long {
        return 0L
    }

    override fun getAll(): MutableMap<String, *> = original.all

    override fun getString(key: String?, defValue: String?): String? = original.getString(key, defValue)

    override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? = original.getStringSet(key, defValues)

    override fun getInt(key: String?, defValue: Int): Int = original.getInt(key, defValue)

    override fun getFloat(key: String?, defValue: Float): Float = original.getFloat(key, defValue)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = original.getBoolean(key, defValue)

    override fun contains(key: String?): Boolean = original.contains(key)

    override fun edit(): SharedPreferences.Editor = original.edit()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        original.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        original.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
