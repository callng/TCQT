package com.tencent.qqnt.kernel.nativeinterface;

public final class SendStatusExtInfo {
    public int resultCode;
    public int rspCode;
    public String rspMsg = "";

    public int getResultCode() {
        return this.resultCode;
    }

    public int getRspCode() {
        return this.rspCode;
    }

    public String getRspMsg() {
        return this.rspMsg;
    }
}
