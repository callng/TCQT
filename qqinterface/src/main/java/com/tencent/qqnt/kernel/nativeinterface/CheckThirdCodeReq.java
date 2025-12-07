package com.tencent.qqnt.kernel.nativeinterface;

public final class CheckThirdCodeReq {
    public AppInfo appInfo;
    public byte[] loginContext;
    public VerifySuccessSign verifySign;
    public WechatReqBody wechatReqBody;

    public AppInfo getAppInfo() {
        return this.appInfo;
    }

    public byte[] getLoginContext() {
        return this.loginContext;
    }

    public VerifySuccessSign getVerifySign() {
        return this.verifySign;
    }

    public WechatReqBody getWechatReqBody() {
        return this.wechatReqBody;
    }
}
