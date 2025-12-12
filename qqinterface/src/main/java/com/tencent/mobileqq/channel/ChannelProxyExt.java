package com.tencent.mobileqq.channel;

public abstract class ChannelProxyExt extends ChannelProxy {

    public void sendMessage(String cmd, byte[] body, long callbackId) {
        throw new RuntimeException("Stub!");
    }

    public abstract void sendMessage(String cmd, byte[] body, String uin, long callbackId);
}
