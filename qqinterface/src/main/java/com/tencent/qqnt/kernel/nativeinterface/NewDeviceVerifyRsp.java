package com.tencent.qqnt.kernel.nativeinterface;

public final class NewDeviceVerifyRsp {
    public byte[] deviceCheckSucceedSig = new byte[0];
    public LoginRspInfo loginRspInfo = new LoginRspInfo();

    public byte[] getDeviceCheckSucceedSig() {
        return this.deviceCheckSucceedSig;
    }

    public LoginRspInfo getLoginRspInfo() {
        return this.loginRspInfo;
    }
}
