package com.tencent.mobileqq.msfcore;

public class MSFPacketCombineConfig implements MSFConfig {
    boolean mIsOpen;
    boolean mIsSupportNTRequest;
    int mMaxCombineWindowTime;
    int mMaxPacketCount;
    int mMaxPacketDelayTime;
    int mMaxPacketSize;
    int mMinPacketSize;
    int mType;

    public MSFPacketCombineConfig() {
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
    }

    public boolean getIsSupportNTRequest() {
        return this.mIsSupportNTRequest;
    }

    public void setIsSupportNTRequest(boolean z) {
        this.mIsSupportNTRequest = z;
    }

    public int getMaxCombineWindowTime() {
        return this.mMaxCombineWindowTime;
    }

    public void setMaxCombineWindowTime(int i) {
        this.mMaxCombineWindowTime = i;
    }

    public int getMaxPacketCount() {
        return this.mMaxPacketCount;
    }

    public void setMaxPacketCount(int i) {
        this.mMaxPacketCount = i;
    }

    public int getMaxPacketDelayTime() {
        return this.mMaxPacketDelayTime;
    }

    public void setMaxPacketDelayTime(int i) {
        this.mMaxPacketDelayTime = i;
    }

    public int getMaxPacketSize() {
        return this.mMaxPacketSize;
    }

    public void setMaxPacketSize(int i) {
        this.mMaxPacketSize = i;
    }

    public int getMinPacketSize() {
        return this.mMinPacketSize;
    }

    public void setMinPacketSize(int i) {
        this.mMinPacketSize = i;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFPacketCombineConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mMaxPacketCount=" + this.mMaxPacketCount +
                ",mMaxPacketSize=" + this.mMaxPacketSize +
                ",mMinPacketSize=" + this.mMinPacketSize +
                ",mMaxPacketDelayTime=" + this.mMaxPacketDelayTime +
                ",mMaxCombineWindowTime=" + this.mMaxCombineWindowTime +
                ",mIsSupportNTRequest=" + this.mIsSupportNTRequest + ",}";
    }

    public MSFPacketCombineConfig(int i, boolean z, int i2, int i3, int i4, int i5, int i6, boolean z2) {
        this.mType = i;
        this.mIsOpen = z;
        this.mMaxPacketCount = i2;
        this.mMaxPacketSize = i3;
        this.mMinPacketSize = i4;
        this.mMaxPacketDelayTime = i5;
        this.mMaxCombineWindowTime = i6;
        this.mIsSupportNTRequest = z2;
    }
}
