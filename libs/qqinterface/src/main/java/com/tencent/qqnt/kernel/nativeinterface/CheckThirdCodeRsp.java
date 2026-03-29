package com.tencent.qqnt.kernel.nativeinterface;

public final class CheckThirdCodeRsp {
    public LoginRspInfo loginRspInfo = new LoginRspInfo();
    public WechatRspBody wechatRspBody;

    public LoginRspInfo getLoginRspInfo() {
        return this.loginRspInfo;
    }

    public WechatRspBody getWechatRspBody() {
        return this.wechatRspBody;
    }
}
