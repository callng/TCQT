package com.tencent.mobileqq.msfcore;

public interface ISoLoader {
    boolean load(String str);

    void reportLoadSoError(String str, boolean z, String str2);
}
