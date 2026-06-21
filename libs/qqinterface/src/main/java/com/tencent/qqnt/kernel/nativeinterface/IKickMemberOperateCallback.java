package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public interface IKickMemberOperateCallback {
    void onResult(int errCode, String errMsg, ArrayList<KickMemberResult> arrayList);
}
