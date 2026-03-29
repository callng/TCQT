package com.tencent.mobileqq.mini.servlet;

import android.os.Bundle;
import com.tencent.mobileqq.pb.MessageMicro;
import mqq.observer.BusinessObserver;

public class MiniAppSSOCmdObserver implements BusinessObserver {

    private static final MiniAppSSOCmdObserver OBSERVER = new MiniAppSSOCmdObserver();

    static MiniAppSSOCmdObserver defaultObserver() {
        return OBSERVER;
    }

    @Override
    public void onReceive(int i, boolean z, Bundle bundle) {
        throw new RuntimeException("Stub!");
    }

    void sendSSOCmdRequest(String str, String str2, MessageMicro<?> messageMicro, MiniAppSSOCmdHelper.MiniAppCmdCallbackInternal<?> miniAppCmdCallbackInternal) {
        throw new RuntimeException("Stub!");
    }
}
