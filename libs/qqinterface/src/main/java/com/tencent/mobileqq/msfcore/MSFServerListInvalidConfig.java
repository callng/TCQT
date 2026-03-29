package com.tencent.mobileqq.msfcore;

public class MSFServerListInvalidConfig implements MSFConfig {
    int mInvalidExpireTime;
    int mIpFailedCountBg;
    int mIpFailedCountFg;
    boolean mIsOpen;
    int mType;

    public MSFServerListInvalidConfig() {
    }

    public int getInvalidExpireTime() {
        return this.mInvalidExpireTime;
    }

    public void setInvalidExpireTime(int i) {
        this.mInvalidExpireTime = i;
    }

    public int getIpFailedCountBg() {
        return this.mIpFailedCountBg;
    }

    public void setIpFailedCountBg(int i) {
        this.mIpFailedCountBg = i;
    }

    public int getIpFailedCountFg() {
        return this.mIpFailedCountFg;
    }

    public void setIpFailedCountFg(int i) {
        this.mIpFailedCountFg = i;
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFServerListInvalidConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mInvalidExpireTime=" + this.mInvalidExpireTime +
                ",mIpFailedCountFg=" + this.mIpFailedCountFg +
                ",mIpFailedCountBg=" + this.mIpFailedCountBg + ",}";
    }

    public MSFServerListInvalidConfig(int i, boolean z, int i2, int i3, int i4) {
        this.mType = i;
        this.mIsOpen = z;
        this.mInvalidExpireTime = i2;
        this.mIpFailedCountFg = i3;
        this.mIpFailedCountBg = i4;
    }
}
