package com.tencent.mobileqq.fe;

public interface EventCallback {
    // attr_key_sec_dispatch_event_ret
    // attr_key_sec_dispatch_event_data
    void onResult(int ret, byte[] data);
}
