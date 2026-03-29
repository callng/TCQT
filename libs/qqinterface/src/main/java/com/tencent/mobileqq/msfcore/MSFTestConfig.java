package com.tencent.mobileqq.msfcore;

public class MSFTestConfig {
    boolean mEnableSideWayConn;
    int mHappyEyeBallThreshold;
    int mQuicTcpRaceThreshold;

    public MSFTestConfig() {
    }

    public boolean getEnableSideWayConn() {
        return this.mEnableSideWayConn;
    }

    public int getHappyEyeBallThreshold() {
        return this.mHappyEyeBallThreshold;
    }

    public int getQuicTcpRaceThreshold() {
        return this.mQuicTcpRaceThreshold;
    }

    public void setEnableSideWayConn(boolean z) {
        this.mEnableSideWayConn = z;
    }

    public void setHappyEyeBallThreshold(int i) {
        this.mHappyEyeBallThreshold = i;
    }

    public void setQuicTcpRaceThreshold(int i) {
        this.mQuicTcpRaceThreshold = i;
    }

    @Override
    public String toString() {
        return "MSFTestConfig{mHappyEyeBallThreshold=" + this.mHappyEyeBallThreshold +
                ",mQuicTcpRaceThreshold=" + this.mQuicTcpRaceThreshold +
                ",mEnableSideWayConn=" + this.mEnableSideWayConn + ",}";
    }

    public MSFTestConfig(int i, int i2, boolean z) {
        this.mHappyEyeBallThreshold = i;
        this.mQuicTcpRaceThreshold = i2;
        this.mEnableSideWayConn = z;
    }
}
