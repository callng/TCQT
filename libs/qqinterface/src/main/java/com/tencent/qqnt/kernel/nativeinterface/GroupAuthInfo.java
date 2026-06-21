package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;
import java.util.ArrayList;

public final class GroupAuthInfo implements Serializable {
    public ArrayList<AuthInfo> authInfoList = new ArrayList<>();
    public OpenAuthInfo openPlatformInfo = new OpenAuthInfo();

    public ArrayList<AuthInfo> getAuthInfoList() {
        return this.authInfoList;
    }

    public OpenAuthInfo getOpenPlatformInfo() {
        return this.openPlatformInfo;
    }
}
