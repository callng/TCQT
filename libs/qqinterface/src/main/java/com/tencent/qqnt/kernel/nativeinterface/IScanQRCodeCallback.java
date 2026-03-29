package com.tencent.qqnt.kernel.nativeinterface;

public interface IScanQRCodeCallback {
    void onResult(int i, String str, ScanQrRsp scanQrRsp);
}
