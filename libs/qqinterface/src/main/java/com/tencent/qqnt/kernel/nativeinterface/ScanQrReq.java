package com.tencent.qqnt.kernel.nativeinterface;

public final class ScanQrReq {
    public QrScanScene scene = QrScanScene.values()[0];
    public byte[] qrSig = new byte[0];
    public String uin = "";

    public byte[] getQrSig() {
        return this.qrSig;
    }

    public QrScanScene getScene() {
        return this.scene;
    }

    public String getUin() {
        return this.uin;
    }
}
