package com.tencent.qqnt.kernel.nativeinterface;

public final class RegisterIdentityRsp {
    public byte[] context;
    public String errorMsg = "";
    public String guide = "";
    public RegisterIdentityLimit limit;
    public int retCode;

    public byte[] getContext() {
        return this.context;
    }

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public String getGuide() {
        return this.guide;
    }

    public RegisterIdentityLimit getLimit() {
        return this.limit;
    }

    public int getRetCode() {
        return this.retCode;
    }
}
