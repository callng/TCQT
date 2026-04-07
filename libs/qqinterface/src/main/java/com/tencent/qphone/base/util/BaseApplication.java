package com.tencent.qphone.base.util;

public abstract class BaseApplication {

    public static BaseApplication getContext() {
        throw new RuntimeException("Stub!");
    }

    public abstract Object getAppData(String str);

    public abstract int getAppId();

    public abstract String getChannelId();

    public abstract int getNTCoreVersion();

    public abstract String getQua();

    public abstract boolean isUserAllow();
}
