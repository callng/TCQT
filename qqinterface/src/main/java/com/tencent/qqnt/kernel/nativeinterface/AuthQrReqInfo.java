package com.tencent.qqnt.kernel.nativeinterface;

public final class AuthQrReqInfo {
    public boolean needA1;
    public byte[] qrSig = new byte[0];
    public QrSwitchOperation opSwitch = QrSwitchOperation.values()[0];
    public String uin = "";

    public boolean getNeedA1() {
        return this.needA1;
    }

    public QrSwitchOperation getOpSwitch() {
        return this.opSwitch;
    }

    public byte[] getQrSig() {
        return this.qrSig;
    }

    public String getUin() {
        return this.uin;
    }
}
