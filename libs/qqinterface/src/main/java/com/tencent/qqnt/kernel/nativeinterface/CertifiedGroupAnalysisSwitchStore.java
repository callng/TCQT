package com.tencent.qqnt.kernel.nativeinterface;

public final class CertifiedGroupAnalysisSwitchStore {
    public long lastOpTime;
    public int state;

    public long getLastOpTime() {
        return this.lastOpTime;
    }

    public int getState() {
        return this.state;
    }
}
