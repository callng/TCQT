package com.tencent.qqnt.kernel.nativeinterface;

public final class GetUpSmsRsp {
    public LoginRspInfo loginRspInfo = new LoginRspInfo();
    public UpSmsInfo upSmsRspRspBody;

    public LoginRspInfo getLoginRspInfo() {
        return this.loginRspInfo;
    }

    public UpSmsInfo getUpSmsRspRspBody() {
        return this.upSmsRspRspBody;
    }
}
