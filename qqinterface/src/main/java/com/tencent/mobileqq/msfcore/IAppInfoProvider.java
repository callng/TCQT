package com.tencent.mobileqq.msfcore;

public interface IAppInfoProvider {
    String getAppIdentifier();

    String getAppVersion();

    int getAppid();

    int getBuildType();

    String getImei();

    int getPatchVersion();

    String getQimei36();

    String getQua();

    String getSandBoxPath();

    String getSystemVersion();
}
