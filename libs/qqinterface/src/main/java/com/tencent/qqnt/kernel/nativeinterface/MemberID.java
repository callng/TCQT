package com.tencent.qqnt.kernel.nativeinterface;

public final class MemberID {
    public long memberUin;
    public String memberUid = "";
    public String memberQid = "";

    public String getMemberQid() {
        return this.memberQid;
    }

    public String getMemberUid() {
        return this.memberUid;
    }

    public long getMemberUin() {
        return this.memberUin;
    }
}
