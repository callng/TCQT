package com.tencent.qqnt.kernel.nativeinterface;

public final class MsgStatus {
    public long msgPushStatus;

    public MsgStatus() {
    }

    public long getMsgPushStatus() {
        return this.msgPushStatus;
    }

    public MsgStatus(long j) {
        this.msgPushStatus = j;
    }
}
