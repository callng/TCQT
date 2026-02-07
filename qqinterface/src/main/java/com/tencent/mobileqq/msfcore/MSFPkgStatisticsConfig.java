package com.tencent.mobileqq.msfcore;

public class MSFPkgStatisticsConfig implements MSFConfig {
    boolean mIsOpen;
    int mRTTLowBound;
    int mRTTUpBound;
    int mThresholdForHeartBeatRtt;
    int mTooLongConnTime;
    int mType;

    public MSFPkgStatisticsConfig() {
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
    }

    public int getRTTLowBound() {
        return this.mRTTLowBound;
    }

    public void setRTTLowBound(int i) {
        this.mRTTLowBound = i;
    }

    public int getRTTUpBound() {
        return this.mRTTUpBound;
    }

    public void setRTTUpBound(int i) {
        this.mRTTUpBound = i;
    }

    public int getThresholdForHeartBeatRtt() {
        return this.mThresholdForHeartBeatRtt;
    }

    public void setThresholdForHeartBeatRtt(int i) {
        this.mThresholdForHeartBeatRtt = i;
    }

    public int getTooLongConnTime() {
        return this.mTooLongConnTime;
    }

    public void setTooLongConnTime(int i) {
        this.mTooLongConnTime = i;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFPkgStatisticsConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mTooLongConnTime=" + this.mTooLongConnTime +
                ",mRTTUpBound=" + this.mRTTUpBound +
                ",mRTTLowBound=" + this.mRTTLowBound +
                ",mThresholdForHeartBeatRtt=" + this.mThresholdForHeartBeatRtt + ",}";
    }

    public MSFPkgStatisticsConfig(int i, boolean z, int i2, int i3, int i4, int i5) {
        this.mType = i;
        this.mIsOpen = z;
        this.mTooLongConnTime = i2;
        this.mRTTUpBound = i3;
        this.mRTTLowBound = i4;
        this.mThresholdForHeartBeatRtt = i5;
    }
}
