package com.tencent.mobileqq.msfcore;

public class MSFSideWayHttpConfig implements MSFConfig {
    int mHappyEyeballsTimeout;
    boolean mIsOpen;
    int mMaxParallelCount;
    int mQuickRetryDelay;
    int mResendIntervalTimeInWiFi;
    int mResendIntervalTimeInXg;
    int mSendDelayTimeInWiFi;
    int mSendDelayTimeInXg;
    int mSidewayMode;
    int mType;

    public MSFSideWayHttpConfig() {
    }

    public int getHappyEyeballsTimeout() {
        return this.mHappyEyeballsTimeout;
    }

    public void setHappyEyeballsTimeout(int i) {
        this.mHappyEyeballsTimeout = i;
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
    }

    public int getMaxParallelCount() {
        return this.mMaxParallelCount;
    }

    public void setMaxParallelCount(int i) {
        this.mMaxParallelCount = i;
    }

    public int getQuickRetryDelay() {
        return this.mQuickRetryDelay;
    }

    public void setQuickRetryDelay(int i) {
        this.mQuickRetryDelay = i;
    }

    public int getResendIntervalTimeInWiFi() {
        return this.mResendIntervalTimeInWiFi;
    }

    public void setResendIntervalTimeInWiFi(int i) {
        this.mResendIntervalTimeInWiFi = i;
    }

    public int getResendIntervalTimeInXg() {
        return this.mResendIntervalTimeInXg;
    }

    public void setResendIntervalTimeInXg(int i) {
        this.mResendIntervalTimeInXg = i;
    }

    public int getSendDelayTimeInWiFi() {
        return this.mSendDelayTimeInWiFi;
    }

    public void setSendDelayTimeInWiFi(int i) {
        this.mSendDelayTimeInWiFi = i;
    }

    public int getSendDelayTimeInXg() {
        return this.mSendDelayTimeInXg;
    }

    public void setSendDelayTimeInXg(int i) {
        this.mSendDelayTimeInXg = i;
    }

    public int getSidewayMode() {
        return this.mSidewayMode;
    }

    public void setSidewayMode(int i) {
        this.mSidewayMode = i;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFSideWayHttpConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mQuickRetryDelay=" + this.mQuickRetryDelay +
                ",mSendDelayTimeInXg=" + this.mSendDelayTimeInXg +
                ",mSendDelayTimeInWiFi=" + this.mSendDelayTimeInWiFi +
                ",mResendIntervalTimeInXg=" + this.mResendIntervalTimeInXg +
                ",mResendIntervalTimeInWiFi=" + this.mResendIntervalTimeInWiFi +
                ",mMaxParallelCount=" + this.mMaxParallelCount +
                ",mSidewayMode=" + this.mSidewayMode +
                ",mHappyEyeballsTimeout=" + this.mHappyEyeballsTimeout + ",}";
    }

    public MSFSideWayHttpConfig(int i, boolean z, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9) {
        this.mType = i;
        this.mIsOpen = z;
        this.mQuickRetryDelay = i2;
        this.mSendDelayTimeInXg = i3;
        this.mSendDelayTimeInWiFi = i4;
        this.mResendIntervalTimeInXg = i5;
        this.mResendIntervalTimeInWiFi = i6;
        this.mMaxParallelCount = i7;
        this.mSidewayMode = i8;
        this.mHappyEyeballsTimeout = i9;
    }
}
