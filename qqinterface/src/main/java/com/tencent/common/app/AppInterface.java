package com.tencent.common.app;

import com.tencent.mobileqq.app.BusinessHandler;
import com.tencent.qphone.base.remote.ToServiceMsg;

import mqq.app.AppRuntime;

public abstract class AppInterface extends AppRuntime {
    public String getCurrentNickname() {
        return "";
    }

    public BusinessHandler getBusinessHandler(String className) {
        return null;
    }

    public void sendToService(ToServiceMsg toServiceMsg) {
    }
}
