package com.tencent.qqnt.kernel.nativeinterface;

public final class FreqLimitInfo {
    public int isLimited;
    public int leftCount;
    public long limitTimestamp;

    public FreqLimitInfo() {
    }

    public int getIsLimited() {
        return this.isLimited;
    }

    public int getLeftCount() {
        return this.leftCount;
    }

    public long getLimitTimestamp() {
        return this.limitTimestamp;
    }

    public FreqLimitInfo(int i, int i2, long j) {
        this.isLimited = i;
        this.leftCount = i2;
        this.limitTimestamp = j;
    }
}
