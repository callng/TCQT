package com.tencent.mobileqq.channel;

public abstract class ChannelProxy {

    public void onReceive(String str, byte[] bArr, boolean z, long j) {
        throw new RuntimeException("Stub!");
    }

    public abstract void sendMessage(String str, byte[] bArr, long j);
}
