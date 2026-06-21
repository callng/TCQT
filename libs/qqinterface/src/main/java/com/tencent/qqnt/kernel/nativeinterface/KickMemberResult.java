package com.tencent.qqnt.kernel.nativeinterface;

public final class KickMemberResult {
    public int result;
    public String uid = "";

    public int getResult() {
        return this.result;
    }

    public String getUid() {
        return this.uid;
    }
}
