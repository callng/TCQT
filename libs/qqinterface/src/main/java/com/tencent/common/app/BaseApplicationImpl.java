package com.tencent.common.app;

import mqq.app.MobileQQ;

public class BaseApplicationImpl extends MobileQQ {

    public static BaseApplicationImpl sApplication;

    public static BaseApplicationImpl getApplication() {
        throw new RuntimeException("Stub!");
    }

    public String getPublishVersion() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public Object getAppData(String str) {
        return null;
    }

    @Override
    public int getAppId() {
        return 0;
    }

    @Override
    public String getChannelId() {
        return "";
    }

    @Override
    public int getNTCoreVersion() {
        return 0;
    }

    @Override
    public String getQua() {
        return "";
    }

    @Override
    public boolean isUserAllow() {
        return false;
    }
}
