package com.tencent.qqnt.kernel.nativeinterface;

public final class MultiTransInfo {
    public int friendFlag;
    public byte[] fromAnonId;
    public String fromFaceUrl;
    public int msgId;
    public int status;

    public MultiTransInfo() {
        this.fromAnonId = new byte[0];
        this.fromFaceUrl = "";
    }

    public int getFriendFlag() {
        return this.friendFlag;
    }

    public byte[] getFromAnonId() {
        return this.fromAnonId;
    }

    public String getFromFaceUrl() {
        return this.fromFaceUrl;
    }

    public int getMsgId() {
        return this.msgId;
    }

    public int getStatus() {
        return this.status;
    }

    public MultiTransInfo(int i, int i2, int i3, byte[] bArr, String str) {
        this.status = i;
        this.msgId = i2;
        this.friendFlag = i3;
        this.fromAnonId = bArr;
        this.fromFaceUrl = str;
    }
}
