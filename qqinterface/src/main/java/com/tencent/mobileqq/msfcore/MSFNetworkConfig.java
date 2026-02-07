package com.tencent.mobileqq.msfcore;

public class MSFNetworkConfig implements MSFConfig {
    boolean mEnableQuicRevertToTcpOnConnFail;
    boolean mEnableQuicRevertToTcpOnIpv6;
    int mNetworkConnMode;
    int mQuickIdleCheckInterval;
    int mQuickIdleCheckTimeout;
    int mType;

    public MSFNetworkConfig() {
    }

    public boolean getEnableQuicRevertToTcpOnConnFail() {
        return this.mEnableQuicRevertToTcpOnConnFail;
    }

    public void setEnableQuicRevertToTcpOnConnFail(boolean z) {
        this.mEnableQuicRevertToTcpOnConnFail = z;
    }

    public boolean getEnableQuicRevertToTcpOnIpv6() {
        return this.mEnableQuicRevertToTcpOnIpv6;
    }

    public void setEnableQuicRevertToTcpOnIpv6(boolean z) {
        this.mEnableQuicRevertToTcpOnIpv6 = z;
    }

    public int getNetworkConnMode() {
        return this.mNetworkConnMode;
    }

    public void setNetworkConnMode(int i) {
        this.mNetworkConnMode = i;
    }

    public int getQuickIdleCheckInterval() {
        return this.mQuickIdleCheckInterval;
    }

    public void setQuickIdleCheckInterval(int i) {
        this.mQuickIdleCheckInterval = i;
    }

    public int getQuickIdleCheckTimeout() {
        return this.mQuickIdleCheckTimeout;
    }

    public void setQuickIdleCheckTimeout(int i) {
        this.mQuickIdleCheckTimeout = i;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFNetworkConfig{" +
                "mType=" + this.mType +
                ",mNetworkConnMode=" + this.mNetworkConnMode +
                ",mEnableQuicRevertToTcpOnConnFail=" + this.mEnableQuicRevertToTcpOnConnFail +
                ",mEnableQuicRevertToTcpOnIpv6=" + this.mEnableQuicRevertToTcpOnIpv6 +
                ",mQuickIdleCheckTimeout=" + this.mQuickIdleCheckTimeout +
                ",mQuickIdleCheckInterval=" + this.mQuickIdleCheckInterval +
                ",}";
    }

    public MSFNetworkConfig(int i, int i2, boolean z, boolean z2, int i3, int i4) {
        this.mType = i;
        this.mNetworkConnMode = i2;
        this.mEnableQuicRevertToTcpOnConnFail = z;
        this.mEnableQuicRevertToTcpOnIpv6 = z2;
        this.mQuickIdleCheckTimeout = i3;
        this.mQuickIdleCheckInterval = i4;
    }
}
