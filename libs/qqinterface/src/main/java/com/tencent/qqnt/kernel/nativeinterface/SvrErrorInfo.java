package com.tencent.qqnt.kernel.nativeinterface;

public final class SvrErrorInfo {
    public int errCode;
    public String errMsg = "";
    public boolean refreshMethodNeedKick;

    public int getErrCode() {
        return this.errCode;
    }

    public String getErrMsg() {
        return this.errMsg;
    }

    public boolean getRefreshMethodNeedKick() {
        return this.refreshMethodNeedKick;
    }
}
