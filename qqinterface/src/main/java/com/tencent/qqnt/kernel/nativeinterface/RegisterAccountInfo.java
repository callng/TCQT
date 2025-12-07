package com.tencent.qqnt.kernel.nativeinterface;

public final class RegisterAccountInfo {
    public byte[] nickname = new byte[0];
    public String password = "";

    public byte[] getNickname() {
        return this.nickname;
    }

    public String getPassword() {
        return this.password;
    }
}
