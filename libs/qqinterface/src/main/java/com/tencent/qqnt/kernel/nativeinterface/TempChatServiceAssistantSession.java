package com.tencent.qqnt.kernel.nativeinterface;

public final class TempChatServiceAssistantSession {
    public long appId;
    public int appType;
    public String appTypeName;

    public TempChatServiceAssistantSession() {
        this.appTypeName = "";
    }

    public long getAppId() {
        return this.appId;
    }

    public int getAppType() {
        return this.appType;
    }

    public String getAppTypeName() {
        return this.appTypeName;
    }

    public TempChatServiceAssistantSession(int i, long j, String str) {
        this.appType = i;
        this.appId = j;
        this.appTypeName = str;
    }
}
