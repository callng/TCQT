package com.tencent.qqnt.kernel.nativeinterface;

public final class UnreadCnt {
    public int cnt;
    public int type;

    public UnreadCnt() {
    }

    public int getCnt() {
        return this.cnt;
    }

    public int getType() {
        return this.type;
    }

    public UnreadCnt(int i, int i2) {
        this.type = i;
        this.cnt = i2;
    }
}
