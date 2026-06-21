package com.tencent.qqnt.kernel.nativeinterface;

import java.util.HashMap;

public interface IGroupMemberUidCallback {
    void onResult(int errCode, String errMsg, HashMap<Long, String> hashMap);
}
