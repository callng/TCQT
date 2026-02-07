package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public interface MSFKernelBridge {
    void appDidEnterBackground();

    void appWillEnterForeground();

    void clearHistorySigns();

    void clearSign(@NonNull String str);

    void clearTestServerList();

    void connect();

    int currentConnType();

    long currentRtt();

    @NonNull
    String currentSSOAddr();

    @NonNull
    String currentSSOIP();

    void disconnect();

    int getApnState();

    @NonNull
    String getClientIP();

    long getConnSeq();

    int getConnectState();

    int getCurrentUsedIPType();

    int getIPStackType();

    @Nullable
    MSFConfigBridge getMSFConfigBridge();

    int getNextPacketSeq();

    int getServerTimeDiff();

    void initMSFCore(int i, @NonNull String str, @NonNull String str2, @NonNull MSFGeneralConfig mSFGeneralConfig);

    boolean isBadNetwork();

    boolean isConnected();

    boolean isConnecting();

    boolean isPullIpOver();

    void notifyLoginSuccess(@NonNull String str, int i, @NonNull String str2, @NonNull byte[] bArr, @NonNull byte[] bArr2, @NonNull byte[] bArr3);

    void notifyLogout();

    void notifyNetworkInfo(int i, int i2, @NonNull MSFExternNetworkInfo mSFExternNetworkInfo);

    void notifyRefreshSign(@NonNull String str, int i, @NonNull String str2, @NonNull byte[] bArr, @NonNull byte[] bArr2, @NonNull byte[] bArr3);

    long recvQueueSize();

    void sendMSFPingCheck(int i);

    int sendPacket(@NonNull MSFRequestAdapter mSFRequestAdapter);

    long sendQueueSize();

    void setCanaryId(@NonNull String str);

    boolean setCertificateData(@NonNull String str);

    void setLightQuicLogListener(int i);

    void setLogLevel(int i);

    void setMSFReportSampleRate(@NonNull HashMap<String, Integer> hashMap);

    void setMSFWhiteList(int i, @NonNull ArrayList<String> arrayList);

    void setPatchVersion(int i);

    void setQimei36(@NonNull String str);

    void setSSOTestEnv(@NonNull String str, int i, @NonNull String str2);

    void setTestServerList(@NonNull ArrayList<String> arrayList, @NonNull ArrayList<String> arrayList2, @NonNull ArrayList<String> arrayList3, @NonNull ArrayList<String> arrayList4, @NonNull MSFTestConfig mSFTestConfig);

    void updateSidewayNetId(long j);
}
