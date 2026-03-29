package com.tencent.mobileqq.msfcore;

import androidx.annotation.Nullable;

public interface UpperBridges {
    @Nullable
    BeaconBridge getBeaconBridge();

    @Nullable
    ILogger getLogger();

    @Nullable
    IMSFCallbackBridge getMSFCallbackBridge();
}
