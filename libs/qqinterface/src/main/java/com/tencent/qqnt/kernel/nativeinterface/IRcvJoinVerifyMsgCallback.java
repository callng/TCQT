package com.tencent.qqnt.kernel.nativeinterface;

public interface IRcvJoinVerifyMsgCallback {
    void onResult(int code, String message, RcvJoinVerifyMsgRsp response);
}
