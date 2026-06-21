package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupGameInfo {
    public String appId = "";
    public String name = "";
    public String iconUrl = "";

    public String getAppId() {
        return this.appId;
    }

    public String getIconUrl() {
        return this.iconUrl;
    }

    public String getName() {
        return this.name;
    }
}
