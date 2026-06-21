package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class OpenAuthInfo implements Serializable {
    public int appType;
    public String name = "";
    public String developerId = "";
    public String appid = "";
    public String source = "";
    public String uniqueCode = "";

    public int getAppType() {
        return this.appType;
    }

    public String getAppid() {
        return this.appid;
    }

    public String getDeveloperId() {
        return this.developerId;
    }

    public String getName() {
        return this.name;
    }

    public String getSource() {
        return this.source;
    }

    public String getUniqueCode() {
        return this.uniqueCode;
    }
}
