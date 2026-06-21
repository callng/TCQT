package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class KickMemberV2Result {
    public long groupCode;
    public ArrayList<KickMemberResult> rptKickResult = new ArrayList<>();

    public long getGroupCode() {
        return this.groupCode;
    }

    public ArrayList<KickMemberResult> getRptKickResult() {
        return this.rptKickResult;
    }
}
