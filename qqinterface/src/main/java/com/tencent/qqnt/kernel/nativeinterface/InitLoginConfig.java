package com.tencent.qqnt.kernel.nativeinterface;

public final class InitLoginConfig {
    public long appid;
    public String bid;
    public boolean needUpdateTicket;
    public String commonPath = "";
    public String clientVer = "";
    public String guid = "";
    public String platVer = "";
    public String hostName = "";
    public LoginPlatformType platform = LoginPlatformType.values()[0];
    public String deviceType = "";
    public String qua = "";
    public String appName = "";
    public String qimei = "";

    public String getAppName() {
        return this.appName;
    }

    public long getAppid() {
        return this.appid;
    }

    public String getBid() {
        return this.bid;
    }

    public String getClientVer() {
        return this.clientVer;
    }

    public String getCommonPath() {
        return this.commonPath;
    }

    public String getDeviceType() {
        return this.deviceType;
    }

    public String getGuid() {
        return this.guid;
    }

    public String getHostName() {
        return this.hostName;
    }

    public boolean getNeedUpdateTicket() {
        return this.needUpdateTicket;
    }

    public String getPlatVer() {
        return this.platVer;
    }

    public LoginPlatformType getPlatform() {
        return this.platform;
    }

    public String getQimei() {
        return this.qimei;
    }

    public String getQua() {
        return this.qua;
    }
}
