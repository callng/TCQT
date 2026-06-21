package com.tencent.qqnt.kernel.nativeinterface;

import java.util.HashMap;

public interface IGroupMemberUinCallback {
    void onResult(int errCode, String errMsg, HashMap<String, Long> hashMap);
}
