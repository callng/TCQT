package com.tencent.qqnt.kernel.nativeinterface;

public interface IKickMemberCallback {
    void onResult(int errCode, String errMsg, KickMemberV2Result kickMemberV2Result);
}
