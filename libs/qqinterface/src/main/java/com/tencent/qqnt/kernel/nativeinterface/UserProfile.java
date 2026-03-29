package com.tencent.qqnt.kernel.nativeinterface;

public final class UserProfile {
    public byte[] nickName = new byte[0];
    public boolean registerWithoutPwd;

    public byte[] getNickName() {
        return this.nickName;
    }

    public boolean getRegisterWithoutPwd() {
        return this.registerWithoutPwd;
    }
}
