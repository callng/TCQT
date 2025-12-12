package com.tencent.mobileqq.channel;

import com.tencent.mobileqq.fe.CmdWhiteListChangeCallback;
import java.util.ArrayList;

public class ChannelManager {

    public static ChannelManager getInstance() {
        throw new RuntimeException("Stub!");
    }

    public native ArrayList<String> getCmdWhiteList();

    public void init(ChannelProxy channelProxy) {
        throw new RuntimeException("Stub!");
    }

    public native void onChannelReady();

    public native void onMsfConnected();

    public native void onNativeReceive(String str, byte[] bArr, boolean z, int i, long j);

    public void onReceive(String str, byte[] bArr, boolean z, int i, long j) {
        throw new RuntimeException("Stub!");
    }

    public void sendMessage(String str, byte[] bArr, long j) {
        throw new RuntimeException("Stub!");
    }

    public native void sendMessageTest();

    public native void setChannelProxy(ChannelProxy channelProxy);

    public native void setCmdWhiteListChangeCallback(CmdWhiteListChangeCallback cmdWhiteListChangeCallback);

    public void setNativeChannel() {
        throw new RuntimeException("Stub!");
    }

    public void onReceive(String str, byte[] bArr, boolean z, long j) {
        throw new RuntimeException("Stub!");
    }
}
