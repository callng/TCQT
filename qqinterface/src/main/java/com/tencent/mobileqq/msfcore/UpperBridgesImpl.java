package com.tencent.mobileqq.msfcore;

import androidx.annotation.Nullable;

public class UpperBridgesImpl implements UpperBridges {
    private final BeaconBridge beaconBridge;
    private final ILogger logger;
    private final IMSFCallbackBridge msfCallbackBridge;
    private final ISoLoader soLoader;

    public UpperBridgesImpl(IMSFCallbackBridge iMSFCallbackBridge, ISoLoader iSoLoader, BeaconBridge beaconBridge, ILogger iLogger) {
        this.msfCallbackBridge = iMSFCallbackBridge;
        this.soLoader = iSoLoader;
        this.beaconBridge = beaconBridge;
        this.logger = iLogger;
    }

    @Nullable
    public BeaconBridge getBeaconBridge() {
        return this.beaconBridge;
    }

    @Nullable
    public ILogger getLogger() {
        return this.logger;
    }

    @Nullable
    public IMSFCallbackBridge getMSFCallbackBridge() {
        return this.msfCallbackBridge;
    }

    @Nullable
    public ISoLoader getSoLoader() {
        return this.soLoader;
    }
}
