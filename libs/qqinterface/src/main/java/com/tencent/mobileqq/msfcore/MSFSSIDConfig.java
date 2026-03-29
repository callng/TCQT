package com.tencent.mobileqq.msfcore;

public class MSFSSIDConfig implements MSFConfig {
    int mHistorySSIDValidity;
    boolean mIsOpen;
    int mType;

    public MSFSSIDConfig() {
    }

    public int getHistorySSIDValidity() {
        return this.mHistorySSIDValidity;
    }

    public void setHistorySSIDValidity(int i) {
        this.mHistorySSIDValidity = i;
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
        return "MSFSSIDConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mHistorySSIDValidity=" + this.mHistorySSIDValidity + ",}";
    }

    public MSFSSIDConfig(int i, boolean z, int i2) {
        this.mType = i;
        this.mIsOpen = z;
        this.mHistorySSIDValidity = i2;
    }
}
