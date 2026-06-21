package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupMemberShutUpInfo {
    public int timeStamp;
    public String uid = "";

    public int getTimeStamp() {
        return this.timeStamp;
    }

    public String getUid() {
        return this.uid;
    }
}
