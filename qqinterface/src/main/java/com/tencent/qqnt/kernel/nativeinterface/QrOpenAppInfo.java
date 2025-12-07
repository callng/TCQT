package com.tencent.qqnt.kernel.nativeinterface;

public final class QrOpenAppInfo {
    public QrOpenAppType appType = QrOpenAppType.values()[0];
    public String comeFrom = "";
    public long openAppid;

    public QrOpenAppType getAppType() {
        return this.appType;
    }

    public String getComeFrom() {
        return this.comeFrom;
    }

    public long getOpenAppid() {
        return this.openAppid;
    }
}
