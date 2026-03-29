package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import java.util.HashMap;

public interface BeaconBridge {
    void report(@NonNull String str, boolean z, long j, long j2, @NonNull HashMap<String, String> hashMap);
}
