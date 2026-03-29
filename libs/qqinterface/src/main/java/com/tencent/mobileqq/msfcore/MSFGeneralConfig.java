package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;

public class MSFGeneralConfig implements MSFConfig {
    String mAppIdentifier;
    String mAppVersion;
    int mBuildType;
    int mDefaultTimeoutInWiFi;
    int mDefaultTimeoutInXg;
    int mDelayConnectToWiFiTimeout;
    int mHeartbeatTimeout;
    int mPingTimeout;
    String mSandBoxPath;
    int mSecSigType;
    String mSysVersion;
    int mType;

    public MSFGeneralConfig() {
        this.mSandBoxPath = "";
        this.mAppIdentifier = "";
        this.mAppVersion = "";
        this.mSysVersion = "";
    }

    @NonNull
    public String getAppIdentifier() {
        return this.mAppIdentifier;
    }

    @NonNull
    public String getAppVersion() {
        return this.mAppVersion;
    }

    public int getBuildType() {
        return this.mBuildType;
    }

    public int getDefaultTimeoutInWiFi() {
        return this.mDefaultTimeoutInWiFi;
    }

    public int getDefaultTimeoutInXg() {
        return this.mDefaultTimeoutInXg;
    }

    public int getDelayConnectToWiFiTimeout() {
        return this.mDelayConnectToWiFiTimeout;
    }

    public int getHeartbeatTimeout() {
        return this.mHeartbeatTimeout;
    }

    public int getPingTimeout() {
        return this.mPingTimeout;
    }

    @NonNull
    public String getSandBoxPath() {
        return this.mSandBoxPath;
    }

    public int getSecSigType() {
        return this.mSecSigType;
    }

    @NonNull
    public String getSysVersion() {
        return this.mSysVersion;
    }

    public int getType() {
        return this.mType;
    }

    public void setAppIdentifier(@NonNull String str) {
        this.mAppIdentifier = str;
    }

    public void setAppVersion(@NonNull String str) {
        this.mAppVersion = str;
    }

    public void setBuildType(int i) {
        this.mBuildType = i;
    }

    public void setDefaultTimeoutInWiFi(int i) {
        this.mDefaultTimeoutInWiFi = i;
    }

    public void setDefaultTimeoutInXg(int i) {
        this.mDefaultTimeoutInXg = i;
    }

    public void setDelayConnectToWiFiTimeout(int i) {
        this.mDelayConnectToWiFiTimeout = i;
    }

    public void setHeartbeatTimeout(int i) {
        this.mHeartbeatTimeout = i;
    }

    public void setPingTimeout(int i) {
        this.mPingTimeout = i;
    }

    public void setSandBoxPath(@NonNull String str) {
        this.mSandBoxPath = str;
    }

    public void setSecSigType(int i) {
        this.mSecSigType = i;
    }

    public void setSysVersion(@NonNull String str) {
        this.mSysVersion = str;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFGeneralConfig{mType=" + this.mType +
                ",mDefaultTimeoutInXg=" + this.mDefaultTimeoutInXg +
                ",mDefaultTimeoutInWiFi=" + this.mDefaultTimeoutInWiFi +
                ",mPingTimeout=" + this.mPingTimeout +
                ",mHeartbeatTimeout=" + this.mHeartbeatTimeout +
                ",mDelayConnectToWiFiTimeout=" + this.mDelayConnectToWiFiTimeout +
                ",mBuildType=" + this.mBuildType +
                ",mSecSigType=" + this.mSecSigType +
                ",mSandBoxPath=" + this.mSandBoxPath +
                ",mAppIdentifier=" + this.mAppIdentifier +
                ",mAppVersion=" + this.mAppVersion +
                ",mSysVersion=" + this.mSysVersion + ",}";
    }

    public MSFGeneralConfig(int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8,
                            @NonNull String str, @NonNull String str2, @NonNull String str3, @NonNull String str4) {
        this.mType = i;
        this.mDefaultTimeoutInXg = i2;
        this.mDefaultTimeoutInWiFi = i3;
        this.mPingTimeout = i4;
        this.mHeartbeatTimeout = i5;
        this.mDelayConnectToWiFiTimeout = i6;
        this.mBuildType = i7;
        this.mSecSigType = i8;
        this.mSandBoxPath = str;
        this.mAppIdentifier = str2;
        this.mAppVersion = str3;
        this.mSysVersion = str4;
    }
}
