package com.tencent.mobileqq.mini.servlet;

import androidx.annotation.NonNull;
import com.tencent.mobileqq.pb.MessageMicro;

public class MiniAppSSOCmdHelper {

    public interface MiniAppCmdCallbackInternal<RESPONSE extends MessageMicro<RESPONSE>> extends MiniAppCmdCallback<RESPONSE> {
        @NonNull
        Class<? extends RESPONSE> getResponseClass();
    }

    public interface MiniAppCmdCallback<RESPONSE extends MessageMicro<RESPONSE>> {
        void onReceived(boolean z, RESPONSE response);
    }

    private MiniAppSSOCmdHelper() {
    }

    public static <REQUEST extends MessageMicro<REQUEST>, RESPONSE extends MessageMicro<RESPONSE>> void sendSSOCmdRequest(@NonNull String str, String str2, @NonNull REQUEST request, @NonNull Class<RESPONSE> cls, MiniAppCmdCallback<RESPONSE> miniAppCmdCallback) {
        throw new RuntimeException("Stub!");
    }
}
