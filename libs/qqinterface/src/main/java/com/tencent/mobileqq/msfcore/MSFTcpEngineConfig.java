package com.tencent.mobileqq.msfcore;

public class MSFTcpEngineConfig implements MSFConfig {
    int mActiveCheckPingInterval;
    int mBackgroundCheckPingInterval;
    int mCheckPingTimeout;
    int mConnMode;
    boolean mIsEnableHearbeatOpt;
    double mParallelConnInterval;
    int mParallelConnNum;
    int mSerialConnNum;
    int mSideWayConnLimit;
    int mType;

    public MSFTcpEngineConfig() {
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

    public int getConnMode() {
        return this.mConnMode;
    }

    public void setConnMode(int i) {
        this.mConnMode = i;
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

    public int getSerialConnNum() {
        return this.mSerialConnNum;
    }

    public void setSerialConnNum(int i) {
        this.mSerialConnNum = i;
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
        return "MSFTcpEngineConfig{mType=" + this.mType +
                ",mSerialConnNum=" + this.mSerialConnNum +
                ",mParallelConnNum=" + this.mParallelConnNum +
                ",mParallelConnInterval=" + this.mParallelConnInterval +
                ",mConnMode=" + this.mConnMode +
                ",mSideWayConnLimit=" + this.mSideWayConnLimit +
                ",mIsEnableHearbeatOpt=" + this.mIsEnableHearbeatOpt +
                ",mCheckPingTimeout=" + this.mCheckPingTimeout +
                ",mActiveCheckPingInterval=" + this.mActiveCheckPingInterval +
                ",mBackgroundCheckPingInterval=" + this.mBackgroundCheckPingInterval + ",}";
    }

    public MSFTcpEngineConfig(int i, int i2, int i3, double d, int i4, int i5, boolean z, int i6, int i7, int i8) {
        this.mType = i;
        this.mSerialConnNum = i2;
        this.mParallelConnNum = i3;
        this.mParallelConnInterval = d;
        this.mConnMode = i4;
        this.mSideWayConnLimit = i5;
        this.mIsEnableHearbeatOpt = z;
        this.mCheckPingTimeout = i6;
        this.mActiveCheckPingInterval = i7;
        this.mBackgroundCheckPingInterval = i8;
    }
}
