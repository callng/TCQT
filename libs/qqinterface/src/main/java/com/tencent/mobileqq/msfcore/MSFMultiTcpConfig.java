package com.tencent.mobileqq.msfcore;

public class MSFMultiTcpConfig implements MSFConfig {
    boolean mIsOpen;
    int mResendIntervalTimeInWiFi;
    int mResendIntervalTimeInXg;
    int mSendDelayTimeInWiFi;
    int mSendDelayTimeInXg;
    int mSidewayMode;
    int mType;

    public MSFMultiTcpConfig() {
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
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
        return "MSFMultiTcpConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mSendDelayTimeInXg=" + this.mSendDelayTimeInXg +
                ",mSendDelayTimeInWiFi=" + this.mSendDelayTimeInWiFi +
                ",mResendIntervalTimeInXg=" + this.mResendIntervalTimeInXg +
                ",mResendIntervalTimeInWiFi=" + this.mResendIntervalTimeInWiFi +
                ",mSidewayMode=" + this.mSidewayMode + ",}";
    }

    public MSFMultiTcpConfig(int i, boolean z, int i2, int i3, int i4, int i5, int i6) {
        this.mType = i;
        this.mIsOpen = z;
        this.mSendDelayTimeInXg = i2;
        this.mSendDelayTimeInWiFi = i3;
        this.mResendIntervalTimeInXg = i4;
        this.mResendIntervalTimeInWiFi = i5;
        this.mSidewayMode = i6;
    }
}
