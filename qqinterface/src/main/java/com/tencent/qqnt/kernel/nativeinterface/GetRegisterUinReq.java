package com.tencent.qqnt.kernel.nativeinterface;

public final class GetRegisterUinReq {
    public BindInfo bindInfo;
    public byte[] context;
    public RegisterAccountInfo registerInfo = new RegisterAccountInfo();

    public BindInfo getBindInfo() {
        return this.bindInfo;
    }

    public byte[] getContext() {
        return this.context;
    }

    public RegisterAccountInfo getRegisterInfo() {
        return this.registerInfo;
    }
}
