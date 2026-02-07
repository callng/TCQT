package com.tencent.mobileqq.msfcore;

public class MSFDualEngineConfig implements MSFConfig {
    int mActiveCheckPingInterval;
    int mBackgroundCheckPingInterval;
    int mCheckPingTimeout;
    boolean mEnable0RTTPing;
    boolean mEnableDynamicSideConnPool;
    boolean mEnableHandshakePersist;
    boolean mEnableIpv6;
    boolean mIsEnableHeartBeatOpt;
    int mMainConnType;
    int mQuicCongestionType;
    int mSideWayConnLimit;
    int mSideWayConnType;
    int mType;

    public MSFDualEngineConfig() {
    }

    public int getActiveCheckPingInterval() {
        return this.mActiveCheckPingInterval;
    }

    public int getBackgroundCheckPingInterval() {
        return this.mBackgroundCheckPingInterval;
    }

    public int getCheckPingTimeout() {
        return this.mCheckPingTimeout;
    }

    public boolean getEnable0RTTPing() {
        return this.mEnable0RTTPing;
    }

    public boolean getEnableDynamicSideConnPool() {
        return this.mEnableDynamicSideConnPool;
    }

    public boolean getEnableHandshakePersist() {
        return this.mEnableHandshakePersist;
    }

    public boolean getEnableIpv6() {
        return this.mEnableIpv6;
    }

    public boolean getIsEnableHeartBeatOpt() {
        return this.mIsEnableHeartBeatOpt;
    }

    public int getMainConnType() {
        return this.mMainConnType;
    }

    public int getQuicCongestionType() {
        return this.mQuicCongestionType;
    }

    public int getSideWayConnLimit() {
        return this.mSideWayConnLimit;
    }

    public int getSideWayConnType() {
        return this.mSideWayConnType;
    }

    public int getType() {
        return this.mType;
    }

    public void setActiveCheckPingInterval(int i) {
        this.mActiveCheckPingInterval = i;
    }

    public void setBackgroundCheckPingInterval(int i) {
        this.mBackgroundCheckPingInterval = i;
    }

    public void setCheckPingTimeout(int i) {
        this.mCheckPingTimeout = i;
    }

    public void setEnable0RTTPing(boolean z) {
        this.mEnable0RTTPing = z;
    }

    public void setEnableDynamicSideConnPool(boolean z) {
        this.mEnableDynamicSideConnPool = z;
    }

    public void setEnableHandshakePersist(boolean z) {
        this.mEnableHandshakePersist = z;
    }

    public void setEnableIpv6(boolean z) {
        this.mEnableIpv6 = z;
    }

    public void setIsEnableHeartBeatOpt(boolean z) {
        this.mIsEnableHeartBeatOpt = z;
    }

    public void setMainConnType(int i) {
        this.mMainConnType = i;
    }

    public void setQuicCongestionType(int i) {
        this.mQuicCongestionType = i;
    }

    public void setSideWayConnLimit(int i) {
        this.mSideWayConnLimit = i;
    }

    public void setSideWayConnType(int i) {
        this.mSideWayConnType = i;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFDualEngineConfig{mType=" + this.mType +
                ",mMainConnType=" + this.mMainConnType +
                ",mSideWayConnType=" + this.mSideWayConnType +
                ",mSideWayConnLimit=" + this.mSideWayConnLimit +
                ",mIsEnableHeartBeatOpt=" + this.mIsEnableHeartBeatOpt +
                ",mCheckPingTimeout=" + this.mCheckPingTimeout +
                ",mActiveCheckPingInterval=" + this.mActiveCheckPingInterval +
                ",mBackgroundCheckPingInterval=" + this.mBackgroundCheckPingInterval +
                ",mEnableDynamicSideConnPool=" + this.mEnableDynamicSideConnPool +
                ",mEnableHandshakePersist=" + this.mEnableHandshakePersist +
                ",mEnable0RTTPing=" + this.mEnable0RTTPing +
                ",mEnableIpv6=" + this.mEnableIpv6 +
                ",mQuicCongestionType=" + this.mQuicCongestionType + ",}";
    }

    public MSFDualEngineConfig(int i, int i2, int i3, int i4, boolean z, int i5, int i6, int i7,
                               boolean z2, boolean z3, boolean z4, boolean z5, int i8) {
        this.mType = i;
        this.mMainConnType = i2;
        this.mSideWayConnType = i3;
        this.mSideWayConnLimit = i4;
        this.mIsEnableHeartBeatOpt = z;
        this.mCheckPingTimeout = i5;
        this.mActiveCheckPingInterval = i6;
        this.mBackgroundCheckPingInterval = i7;
        this.mEnableDynamicSideConnPool = z2;
        this.mEnableHandshakePersist = z3;
        this.mEnable0RTTPing = z4;
        this.mEnableIpv6 = z5;
        this.mQuicCongestionType = i8;
    }
}
