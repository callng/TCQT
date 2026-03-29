package com.tencent.mobileqq.msfcore;

public class MSFQuicEngineConfig implements MSFConfig {
    int mActiveCheckPingInterval;
    int mBackgroundCheckPingInterval;
    int mCheckPingTimeout;
    boolean mEnable0RTTPing;
    boolean mEnableHandshakePersist;
    boolean mEnableIpv6;
    boolean mIsEnableHearbeatOpt;
    double mParallelConnInterval;
    int mParallelConnNum;
    int mQuicCongestionType;
    int mSideWayConnLimit;
    int mType;

    public MSFQuicEngineConfig() {
    }

    public int getActiveCheckPingInterval() {
        return this.mActiveCheckPingInterval;
    }

    public void setActiveCheckPingInterval(int i) {
        this.mActiveCheckPingInterval = i;
    }

    public int getBackgroundCheckPingInterval() {
        return this.mBackgroundCheckPingInterval;
    }

    public void setBackgroundCheckPingInterval(int i) {
        this.mBackgroundCheckPingInterval = i;
    }

    public int getCheckPingTimeout() {
        return this.mCheckPingTimeout;
    }

    public void setCheckPingTimeout(int i) {
        this.mCheckPingTimeout = i;
    }

    public boolean getEnable0RTTPing() {
        return this.mEnable0RTTPing;
    }

    public void setEnable0RTTPing(boolean z) {
        this.mEnable0RTTPing = z;
    }

    public boolean getEnableHandshakePersist() {
        return this.mEnableHandshakePersist;
    }

    public void setEnableHandshakePersist(boolean z) {
        this.mEnableHandshakePersist = z;
    }

    public boolean getEnableIpv6() {
        return this.mEnableIpv6;
    }

    public void setEnableIpv6(boolean z) {
        this.mEnableIpv6 = z;
    }

    public boolean getIsEnableHearbeatOpt() {
        return this.mIsEnableHearbeatOpt;
    }

    public void setIsEnableHearbeatOpt(boolean z) {
        this.mIsEnableHearbeatOpt = z;
    }

    public double getParallelConnInterval() {
        return this.mParallelConnInterval;
    }

    public void setParallelConnInterval(double d) {
        this.mParallelConnInterval = d;
    }

    public int getParallelConnNum() {
        return this.mParallelConnNum;
    }

    public void setParallelConnNum(int i) {
        this.mParallelConnNum = i;
    }

    public int getQuicCongestionType() {
        return this.mQuicCongestionType;
    }

    public void setQuicCongestionType(int i) {
        this.mQuicCongestionType = i;
    }

    public int getSideWayConnLimit() {
        return this.mSideWayConnLimit;
    }

    public void setSideWayConnLimit(int i) {
        this.mSideWayConnLimit = i;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFQuicEngineConfig{mType=" + this.mType +
                ",mParallelConnNum=" + this.mParallelConnNum +
                ",mParallelConnInterval=" + this.mParallelConnInterval +
                ",mSideWayConnLimit=" + this.mSideWayConnLimit +
                ",mIsEnableHearbeatOpt=" + this.mIsEnableHearbeatOpt +
                ",mCheckPingTimeout=" + this.mCheckPingTimeout +
                ",mActiveCheckPingInterval=" + this.mActiveCheckPingInterval +
                ",mBackgroundCheckPingInterval=" + this.mBackgroundCheckPingInterval +
                ",mEnableHandshakePersist=" + this.mEnableHandshakePersist +
                ",mEnable0RTTPing=" + this.mEnable0RTTPing +
                ",mEnableIpv6=" + this.mEnableIpv6 +
                ",mQuicCongestionType=" + this.mQuicCongestionType + ",}";
    }

    public MSFQuicEngineConfig(int i, int i2, double d, int i3, boolean z, int i4, int i5, int i6,
                               boolean z2, boolean z3, boolean z4, int i7) {
        this.mType = i;
        this.mParallelConnNum = i2;
        this.mParallelConnInterval = d;
        this.mSideWayConnLimit = i3;
        this.mIsEnableHearbeatOpt = z;
        this.mCheckPingTimeout = i4;
        this.mActiveCheckPingInterval = i5;
        this.mBackgroundCheckPingInterval = i6;
        this.mEnableHandshakePersist = z2;
        this.mEnable0RTTPing = z3;
        this.mEnableIpv6 = z4;
        this.mQuicCongestionType = i7;
    }
}
