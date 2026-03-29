package com.tencent.qqnt.kernel.nativeinterface;

public final class SubmitIdentityInfoReq {
    public byte[] identity;
    public UserInfo phoneInfo;
    public int type;

    public byte[] getIdentity() {
        return this.identity;
    }

    public UserInfo getPhoneInfo() {
        return this.phoneInfo;
    }

    public int getType() {
        return this.type;
    }
}
