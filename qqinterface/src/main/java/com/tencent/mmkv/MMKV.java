package com.tencent.mmkv;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MMKV implements SharedPreferences, SharedPreferences.Editor {

    public static native long backupAllToDirectory(String str);

    public static native boolean backupOneToDirectory(String str, String str2, @Nullable String str3);

    public static native boolean isFileValid(String str, @Nullable String str2);

    public static native void onExit();

    public static native int pageSize();

    public static native long restoreAllFromDirectory(String str);

    public static native boolean restoreOneMMKVFromDirectory(String str, String str2, @Nullable String str3);

    public static native void setLoadOnNecessaryEnable(boolean z);

    public static native void setSharedLockFirstWhenReload(boolean z);

    public static native String version();

    public native int ashmemFD();

    public native int ashmemMetaFD();

    public native void checkContentChangedByOuterProcess();

    public native void checkReSetCryptKey(@Nullable String str);

    public native void clearAll();

    public native void clearAllWithKeepingSpace();

    public native void clearMemoryCache();

    public native void close();

    @Nullable
    public native String cryptKey();

    public native boolean disableAutoKeyExpire();

    public native void disableCompareBeforeSet();

    public native boolean enableAutoKeyExpire(int i2);

    public native void lock();

    public native String mmapID();

    public native boolean reKey(@Nullable String str);

    public native void removeValuesForKeys(String[] strArr);

    public native void trim();

    public native boolean tryLock();

    public native void unlock();

    @Override
    public boolean contains(String key) {
        return false;
    }

    @Override
    public Editor edit() {
        return null;
    }

    @Override
    public Map<String, ?> getAll() {
        return Collections.emptyMap();
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return false;
    }

    @Override
    public float getFloat(String key, float defValue) {
        return 0;
    }

    @Override
    public int getInt(String key, int defValue) {
        return 0;
    }

    @Override
    public long getLong(String key, long defValue) {
        return 0;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return "";
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return Collections.emptySet();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {

    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {

    }

    @Override
    public void apply() {

    }

    @Override
    public Editor clear() {
        return null;
    }

    @Override
    public boolean commit() {
        return false;
    }

    @Override
    public Editor putBoolean(String key, boolean value) {
        return null;
    }

    @Override
    public Editor putFloat(String key, float value) {
        return null;
    }

    @Override
    public Editor putInt(String key, int value) {
        return null;
    }

    @Override
    public Editor putLong(String key, long value) {
        return null;
    }

    @Override
    public Editor putString(String key, @Nullable String value) {
        return null;
    }

    @Override
    public Editor putStringSet(String key, @Nullable Set<String> values) {
        return null;
    }

    @Override
    public Editor remove(String key) {
        return null;
    }
}
