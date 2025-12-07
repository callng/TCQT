package com.tencent.qqnt.kernel.nativeinterface;

public final class GetRegisterUinRsp {
    public String errorMsg = "";
    public String registerSucceedToken = "";
    public int retCode;
    public long uin;

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public String getRegisterSucceedToken() {
        return this.registerSucceedToken;
    }

    public int getRetCode() {
        return this.retCode;
    }

    public long getUin() {
        return this.uin;
    }
}
