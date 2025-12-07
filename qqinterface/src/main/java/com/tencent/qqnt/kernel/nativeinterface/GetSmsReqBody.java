package com.tencent.qqnt.kernel.nativeinterface;

public final class GetSmsReqBody {
    public AppInfo appInfo;
    public byte[] loginContext;
    public byte[] longCookie;
    public UserInfo userInfo = new UserInfo();
    public VerifySuccessSign verifySign;

    public AppInfo getAppInfo() {
        return this.appInfo;
    }

    public byte[] getLoginContext() {
        return this.loginContext;
    }

    public byte[] getLongCookie() {
        return this.longCookie;
    }

    public UserInfo getUserInfo() {
        return this.userInfo;
    }

    public VerifySuccessSign getVerifySign() {
        return this.verifySign;
    }
}
