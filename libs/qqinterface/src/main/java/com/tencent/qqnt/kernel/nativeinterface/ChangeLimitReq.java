package com.tencent.qqnt.kernel.nativeinterface;

public final class ChangeLimitReq {
    public byte[] context;
    public long identityLimitType;

    public byte[] getContext() {
        return this.context;
    }

    public long getIdentityLimitType() {
        return this.identityLimitType;
    }
}
