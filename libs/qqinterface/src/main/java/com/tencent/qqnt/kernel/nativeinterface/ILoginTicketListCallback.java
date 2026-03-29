package com.tencent.qqnt.kernel.nativeinterface;

import java.util.HashMap;

public interface ILoginTicketListCallback {
    void onResult(int i, String str, HashMap<Long, LoginTicketInfo> hashMap);
}
