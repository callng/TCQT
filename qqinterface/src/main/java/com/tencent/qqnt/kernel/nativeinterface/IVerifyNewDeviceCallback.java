package com.tencent.qqnt.kernel.nativeinterface;

public interface IVerifyNewDeviceCallback {
    void onResult(int i, String str, NewDeviceVerifyRsp newDeviceVerifyRsp);
}
