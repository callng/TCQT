package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;

public interface IMSFCallbackBridge {
    void onMSFApnState(int i);

    void onMSFBadNetworkState(boolean z, int i);

    void onMSFError(int i, @NonNull String str, @NonNull String str2, @NonNull String str3, @NonNull String str4);

    void onMSFNetworkState(int i, int i2);

    void onMSFPacketState(@NonNull MSFResponseAdapter mSFResponseAdapter);

    void onMSFServerTimeUpdated(int i);

    void onSSORetError(int i, @NonNull String str, @NonNull String str2, @NonNull String str3);
}
