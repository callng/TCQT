package com.tencent.mobileqq.msf.core;

import com.tencent.qphone.base.remote.ToServiceMsg;

public class MsfCore {
    public static MsfCore sCore = null;

    public int sendSsoMsg(ToServiceMsg toServiceMsg) {
        return -1;
    }

    public String getMainAccount() {
        return "0";
    }

    public static synchronized int getNextSeq() {
        return 0;
    }
}
