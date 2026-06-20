package com.tencent.qphone.base.util.report;

import java.util.HashMap;

public interface IQLogReport {
    void sendToBeacon(String str, HashMap<String, String> hashMap);
}
