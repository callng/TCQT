package com.tencent.qqnt.kernel.nativeinterface;

public final class KickMemberInfo {
    public int optFlag;
    public int optOperate;
    public String optMemberUid = "";
    public String optBytesMsg = "";

    public String getOptBytesMsg() {
        return this.optBytesMsg;
    }

    public int getOptFlag() {
        return this.optFlag;
    }

    public String getOptMemberUid() {
        return this.optMemberUid;
    }

    public int getOptOperate() {
        return this.optOperate;
    }
}
