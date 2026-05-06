package com.tencent.qqnt.kernel.nativeinterface;

public final class MsgBoxNecessaryMsgInfo {
    public String highlightDigest;
    public long msgSeq;
    public long msgTime;

    public MsgBoxNecessaryMsgInfo() {
        this.highlightDigest = "";
    }

    public String getHighlightDigest() {
        return this.highlightDigest;
    }

    public long getMsgSeq() {
        return this.msgSeq;
    }

    public long getMsgTime() {
        return this.msgTime;
    }

    public MsgBoxNecessaryMsgInfo(long j, long j2, String str) {
        this.msgSeq = j;
        this.msgTime = j2;
        this.highlightDigest = str;
    }
}
