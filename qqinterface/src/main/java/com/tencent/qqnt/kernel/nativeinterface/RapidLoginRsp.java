package com.tencent.qqnt.kernel.nativeinterface;

public final class RapidLoginRsp {
    public int retCode;
    public String errorMsg = "";
    public String url = "";

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public int getRetCode() {
        return this.retCode;
    }

    public String getUrl() {
        return this.url;
    }
}
