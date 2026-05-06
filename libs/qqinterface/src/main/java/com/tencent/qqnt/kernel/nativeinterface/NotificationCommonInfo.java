package com.tencent.qqnt.kernel.nativeinterface;

public final class NotificationCommonInfo {
    public long msgListUnreadCnt;

    public NotificationCommonInfo() {
    }

    public long getMsgListUnreadCnt() {
        return this.msgListUnreadCnt;
    }

    public NotificationCommonInfo(long j) {
        this.msgListUnreadCnt = j;
    }
}
