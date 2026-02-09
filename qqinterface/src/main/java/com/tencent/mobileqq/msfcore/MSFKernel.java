package com.tencent.mobileqq.msfcore;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

public class MSFKernel {
    public static final int BUILD_TYPE_CI = 2;
    public static final int BUILD_TYPE_GRAY_VERSION = 1;
    public static final int BUILD_TYPE_PUBLICATION = 0;
    private static volatile MSFKernel INSTANCE = null;
    public static final int IP_TYPE_DUAL = 3;
    public static final int IP_TYPE_IPV4 = 1;
    public static final int IP_TYPE_IPV6 = 2;
    public static final int IP_TYPE_NONE = 0;
    public static final String LIGHT_QUIC_LIB = "LightQuic";
    public static final int MSF_APN_Reachable_WIFI = 2;
    public static final int MSF_APN_Reachable_WWAN = 1;
    public static final int MSF_APN_UNKNOWN = 0;
    public static final int MSF_APN_Unreachable = 3;
    public static final int MSF_BAD_NETWORK_REASON_CONN_TIME_TOO_LONG = 1;
    public static final int MSF_BAD_NETWORK_REASON_HEARTBEAT_PKG_COST_TOO_LONG = 16;
    public static final int MSF_BAD_NETWORK_REASON_NONE = 0;
    public static final int MSF_BAD_NETWORK_REASON_PACKET_BLOCK = 8;
    public static final int MSF_BAD_NETWORK_REASON_PING_TIME_TOO_LONG = 2;
    public static final int MSF_BAD_NETWORK_REASON_RTTS_TOO_LONG = 4;
    public static final int MSF_CONFIG_SERVER_LIST_INVALID = 14;
    public static final int MSF_CONFIG_TYPE_DUAL_ENGINE = 13;
    public static final int MSF_CONFIG_TYPE_GENERAL = 0;
    public static final int MSF_CONFIG_TYPE_MODIFY_SWITCH = 8;
    public static final int MSF_CONFIG_TYPE_MULTI_TCP = 4;
    public static final int MSF_CONFIG_TYPE_NETWORK_CONFIGURE = 9;
    public static final int MSF_CONFIG_TYPE_PACKAGE_COMBINE = 1;
    public static final int MSF_CONFIG_TYPE_PKG_COMPRESS = 7;
    public static final int MSF_CONFIG_TYPE_PKG_STATISTICS = 6;
    public static final int MSF_CONFIG_TYPE_QUIC_ENGINE = 10;
    public static final int MSF_CONFIG_TYPE_SIDEWAY_HTTP = 2;
    public static final int MSF_CONFIG_TYPE_SIDEWAY_UDP = 3;
    public static final int MSF_CONFIG_TYPE_SSID = 11;
    public static final int MSF_CONFIG_TYPE_TCP_ENGINE = 5;
    public static final int MSF_CONFIG_TYPE_TRACE = 12;
    public static final int MSF_CONN_MODE_DUAL = 6;
    public static final int MSF_CONN_MODE_HTTP = 2;
    public static final int MSF_CONN_MODE_HYBRID = 5;
    public static final int MSF_CONN_MODE_QUIC = 4;
    public static final int MSF_CONN_MODE_SIDEWAY_HTTP = 3;
    public static final int MSF_CONN_MODE_TCP = 1;
    public static final int MSF_CONN_MODE_UNKNOWN = 0;
    public static final int MSF_CONN_TYPE_HTTP_LONG = 3;
    public static final int MSF_CONN_TYPE_HTTP_SHORT = 4;
    public static final int MSF_CONN_TYPE_MULTI_TCP = 2;
    public static final int MSF_CONN_TYPE_QUIC = 5;
    public static final int MSF_CONN_TYPE_TCP_LONG = 1;
    public static final int MSF_CONN_TYPE_UDP = 6;
    public static final int MSF_CONN_TYPE_UNKNOWN = 0;
    public static final int MSF_ERROR_ILLEGAL_APP = 1;
    public static final int MSF_ERROR_NOT_GRAY_USER = 2;
    public static final String MSF_KERNEL_LIB = "MSFKernel";
    public static final int MSF_LOG_LEVEL_ALL = 9;
    public static final int MSF_LOG_LEVEL_DEBUG = 6;
    public static final int MSF_LOG_LEVEL_ERROR = 2;
    public static final int MSF_LOG_LEVEL_EVENT = 1;
    public static final int MSF_LOG_LEVEL_INFO = 5;
    public static final int MSF_LOG_LEVEL_METHOD = 8;
    public static final int MSF_LOG_LEVEL_NOTICE = 4;
    public static final int MSF_LOG_LEVEL_OFF = 0;
    public static final int MSF_LOG_LEVEL_TRACE = 7;
    public static final int MSF_LOG_LEVEL_WARN = 3;
    public static final int MSF_PACKET_FAIL = 4;
    public static final int MSF_PACKET_FAIL_FLOW_SAVE_FILTERED = 9;
    public static final int MSF_PACKET_FAIL_LOGIC_ERROR = 11;
    public static final int MSF_PACKET_FAIL_NO_AUTH_ON_REQ = 3;
    public static final int MSF_PACKET_FAIL_NO_NET_ON_REQ = 1;
    public static final int MSF_PACKET_FAIL_NO_NET_ON_RESP = 2;
    public static final int MSF_PACKET_FAIL_NO_RESEND_ON_REQ = 7;
    public static final int MSF_PACKET_FAIL_NO_RESEND_ON_RESP = 8;
    public static final int MSF_PACKET_FAIL_OVER_LOAD_ON_REQ = 10;
    public static final int MSF_PACKET_FAIL_SSO_ERROR = 4;
    public static final int MSF_PACKET_FAIL_TIMEOUT_ON_REQ = 5;
    public static final int MSF_PACKET_FAIL_TIMEOUT_ON_RESP = 6;
    public static final int MSF_PACKET_FAIL_UNKNOWN = 0;
    public static final int MSF_PACKET_RECEIVE_WAY_HTTP = 2;
    public static final int MSF_PACKET_RECEIVE_WAY_MULTI_PATH = 4;
    public static final int MSF_PACKET_RECEIVE_WAY_ONE_SECOND_HTTP = 5;
    public static final int MSF_PACKET_RECEIVE_WAY_ONE_SECOND_UDP = 8;
    public static final int MSF_PACKET_RECEIVE_WAY_SIDE_WAY_HTTP = 3;
    public static final int MSF_PACKET_RECEIVE_WAY_SIDE_WAY_HTTP_UNIQUE = 6;
    public static final int MSF_PACKET_RECEIVE_WAY_SIDE_WAY_QUIC_FOR_HTTP = 7;
    public static final int MSF_PACKET_RECEIVE_WAY_SIDE_WAY_TCP = 10;
    public static final int MSF_PACKET_RECEIVE_WAY_TCP = 1;
    public static final int MSF_PACKET_RECEIVE_WAY_UDP = 9;
    public static final int MSF_PACKET_RECEIVE_WAY_UNKNOWN = 0;
    public static final int MSF_PACKET_RECV_WAY_HTTP = 2;
    public static final int MSF_PACKET_RECV_WAY_MULTI_PATH = 4;
    public static final int MSF_PACKET_RECV_WAY_ONE_SECOND_HTTP = 5;
    public static final int MSF_PACKET_RECV_WAY_ONE_SECOND_UDP = 8;
    public static final int MSF_PACKET_RECV_WAY_QUIC = 11;
    public static final int MSF_PACKET_RECV_WAY_SIDE_WAY_HTTP = 3;
    public static final int MSF_PACKET_RECV_WAY_SIDE_WAY_HTTP_UNIQUE = 6;
    public static final int MSF_PACKET_RECV_WAY_SIDE_WAY_QUIC = 12;
    public static final int MSF_PACKET_RECV_WAY_SIDE_WAY_QUIC_FOR_HTTP = 7;
    public static final int MSF_PACKET_RECV_WAY_SIDE_WAY_TCP = 10;
    public static final int MSF_PACKET_RECV_WAY_TCP = 1;
    public static final int MSF_PACKET_RECV_WAY_UDP = 9;
    public static final int MSF_PACKET_RECV_WAY_UNKNOWN = 0;
    public static final int MSF_PACKET_SEND_OK = 2;
    public static final int MSF_PACKET_SUCCESS = 3;
    public static final int MSF_PACKET_TCP_SEND_SUCCESS = 5;
    public static final int MSF_PACKET_TO_SEND = 1;
    public static final int MSF_PACKET_UNKNOWN = 0;
    public static final int MSF_QUIC_CONGESTION_TYPE_BBR = 2;
    public static final int MSF_QUIC_CONGESTION_TYPE_BBR_V2 = 5;
    public static final int MSF_QUIC_CONGESTION_TYPE_CUBIC = 0;
    public static final int MSF_QUIC_CONGESTION_TYPE_GOOGCC = 4;
    public static final int MSF_QUIC_CONGESTION_TYPE_PCC = 3;
    public static final int MSF_QUIC_CONGESTION_TYPE_RENO = 1;
    public static final int MSF_SEND_PACKET_TYPE_AFTER_LOGOUT = 3;
    public static final int MSF_SEND_PACKET_TYPE_AFTER_LOGOUT_END = 4;
    public static final int MSF_SEND_PACKET_TYPE_NORMAL = 1;
    public static final int MSF_SEND_PACKET_TYPE_UNKNOWN = 0;
    public static final int MSF_SEND_PACKET_TYPE_WITH_UIN = 2;
    public static final int MSF_SEND_PRIORITY_HIGH = 100;
    public static final int MSF_SEND_PRIORITY_LOW = 50;
    public static final int MSF_SEND_PRIORITY_MEDIUM = 75;
    public static final int MSF_SEND_PRIORITY_MIN = 0;
    public static final int MSF_SEND_WITHOUT_COMBINE = 8;
    public static final int MSF_SEND_WITHOUT_RESEND = 2;
    public static final int MSF_SEND_WITHOUT_SIDE_WAY = 16;
    public static final int MSF_SEND_WITH_ABORT_WHEN_NO_NET = 4;
    public static final int MSF_SEND_WITH_ANSWER = 1;
    public static final int MSF_SEND_WITH_FORCE_SEC_SING = 32;
    public static final int MSF_SEND_WITH_NONE = 0;
    public static final int MSF_SEND_WITH_UID_CHECK = 64;
    public static final int MSF_SIDE_WAY_MODE_ALL = 1;
    public static final int MSF_SIDE_WAY_MODE_DEFAULT = 2;
    public static final int MSF_TCP_ENGINE_CONN_MODE_HYBRID = 3;
    public static final int MSF_TCP_ENGINE_CONN_MODE_PARALLEL = 2;
    public static final int MSF_TCP_ENGINE_CONN_MODE_SERIAL = 1;
    public static final int MSF_UIN_TYPE_MAIL = 1;
    public static final int MSF_UIN_TYPE_QQ = 0;
    public static final int MSF_WHITE_LIST_TYPE_MULTI_TCP = 4;
    public static final int MSF_WHITE_LIST_TYPE_PACKAGE_COMBINE = 16;
    public static final int MSF_WHITE_LIST_TYPE_REGISTER_PROXY = 32;
    public static final int MSF_WHITE_LIST_TYPE_SEC_SIG = 8;
    public static final int MSF_WHITE_LIST_TYPE_SIDE_WAY_HTTP = 1;
    public static final int MSF_WHITE_LIST_TYPE_SIDE_WAY_UDP = 2;
    public static final int NETWORK_CONNECT_FAIL = 4;
    public static final int NETWORK_CONNECT_SUCCESS = 3;
    public static final int NETWORK_CONNECT_TOO_FREQUENT = 1;
    public static final int NETWORK_DETECTOR_SHOW_NET_ERROR = 8;
    public static final int NETWORK_DETECTOR_SHOW_NET_UNREACHABLE = 9;
    public static final int NETWORK_DETECTOR_SHOW_NET_WEB_AUTH = 7;
    public static final int NETWORK_DISCONNECT = 5;
    public static final int NETWORK_RECV_PING = 10;
    public static final int NETWORK_SHOW_NET_ERROR = 6;
    public static final int NETWORK_STATUS_NOT_REACHABLE = 0;
    public static final int NETWORK_STATUS_VIA_WAN = 2;
    public static final int NETWORK_STATUS_VIA_WIFI = 1;
    public static final int NETWORK_TO_CONNECT = 2;
    public static final int NETWORK_UNKNOWN = 0;
    public static final int SSO_SEC_SIG_ALL = 1;
    public static final int SSO_SEC_SIG_NONE = 0;
    public static final int SSO_SEC_SIG_SOME = 2;
    public static final String TAG = "MSFKernel";

    MSFKernel(UpperBridgesImpl upperBridgesImpl, boolean z) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public static MSFKernel getInstance(UpperBridgesImpl upperBridgesImpl, boolean z) {
        if (INSTANCE == null) {
            synchronized (MSFKernel.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MSFKernel(upperBridgesImpl, z);
                }
            }
        }
        return INSTANCE;
    }

    @Nullable
    public static MSFKernel getOrNull() {
        return INSTANCE;
    }

    public int sendPacket(MSFRequestAdapter mSFRequestAdapter) {
        throw new RuntimeException("Stub!");
    }

    public int connectState() {
        throw new RuntimeException("Stub!");
    }

    public int currentConnType() {
        throw new RuntimeException("Stub!");
    }

    public long currentRtt() {
        throw new RuntimeException("Stub!");
    }

    public String currentSSOAddr() {
        throw new RuntimeException("Stub!");
    }

    public String currentSSOIP() {
        throw new RuntimeException("Stub!");
    }

    public void disconnect() {
        throw new RuntimeException("Stub!");
    }

    public int getApnState() {
        throw new RuntimeException("Stub!");
    }

    public String getClientIP() {
        throw new RuntimeException("Stub!");
    }

    public long getConnSeq() {
        throw new RuntimeException("Stub!");
    }

    public int getCurrentUsedIPType() {
        throw new RuntimeException("Stub!");
    }

    public int getIPStackType() {
        throw new RuntimeException("Stub!");
    }

    public int getNextPacketSeq() {
        throw new RuntimeException("Stub!");
    }

    public int getServerTimeDiff() {
        throw new RuntimeException("Stub!");
    }

    public void init(Context context, IAppInfoProvider iAppInfoProvider, IMSFCallbackBridge iMSFCallbackBridge) {
        throw new RuntimeException("Stub!");
    }

    public boolean isBadNetwork() {
        throw new RuntimeException("Stub!");
    }

    public boolean isConnected() {
        throw new RuntimeException("Stub!");
    }

    public boolean isConnecting() {
        throw new RuntimeException("Stub!");
    }

    public boolean isPullIpOver() {
        throw new RuntimeException("Stub!");
    }

    public void notifyLoginSuccess(String str, int i, String str2, byte[] bArr, byte[] bArr2, byte[] bArr3) {
        throw new RuntimeException("Stub!");
    }

    public void notifyLogout() {
        throw new RuntimeException("Stub!");
    }

    public void notifyNetworkInfo(int i, int i2, MSFExternNetworkInfo mSFExternNetworkInfo) {
        throw new RuntimeException("Stub!");
    }

    public void notifyRefreshSign(String str, int i, String str2, byte[] bArr, byte[] bArr2, byte[] bArr3) {
        throw new RuntimeException("Stub!");
    }

    public void onAppBackground() {
        throw new RuntimeException("Stub!");
    }

    public void onAppForeground() {
        throw new RuntimeException("Stub!");
    }

    public long recvQueueSize() {
        throw new RuntimeException("Stub!");
    }

    public void sendMSFPingCheck(int i) {
        throw new RuntimeException("Stub!");
    }

    public long sendQueueSize() {
        throw new RuntimeException("Stub!");
    }

    public void setCanaryId(String str) {
        throw new RuntimeException("Stub!");
    }

    public boolean setCertificateData(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setLogLevel(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setMSFConfig(int i, MSFConfig mSFConfig) {
        throw new RuntimeException("Stub!");
    }

    public void setMSFReportSampleRate(HashMap<String, Integer> hashMap) {
        throw new RuntimeException("Stub!");
    }

    public void setMSFWhiteList(int i, ArrayList<String> arrayList) {
        throw new RuntimeException("Stub!");
    }

    public void setPatchVersion(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setQimei36(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setSSOTestEnv(String str, int i, String str2) {
        throw new RuntimeException("Stub!");
    }

    public void setTestServerList(ArrayList<String> arrayList, ArrayList<String> arrayList2, ArrayList<String> arrayList3, ArrayList<String> arrayList4, MSFTestConfig mSFTestConfig) {
        throw new RuntimeException("Stub!");
    }

    public void updateSidewayNetId(long j) {
        throw new RuntimeException("Stub!");
    }
}
