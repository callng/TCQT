package com.tencent.qqnt.kernel.nativeinterface;

public final class LoginTicketInfo {
    public long appId;
    public long expireTime;
    public long generateTime;
    public long nextRefreshTime;
    public byte[] a1 = new byte[0];
    public byte[] a2 = new byte[0];
    public byte[] d2 = new byte[0];
    public byte[] d2Key = new byte[0];

    public byte[] getA1() {
        return this.a1;
    }

    public byte[] getA2() {
        return this.a2;
    }

    public long getAppId() {
        return this.appId;
    }

    public byte[] getD2() {
        return this.d2;
    }

    public byte[] getD2Key() {
        return this.d2Key;
    }

    public long getExpireTime() {
        return this.expireTime;
    }

    public long getGenerateTime() {
        return this.generateTime;
    }

    public long getNextRefreshTime() {
        return this.nextRefreshTime;
    }
}
