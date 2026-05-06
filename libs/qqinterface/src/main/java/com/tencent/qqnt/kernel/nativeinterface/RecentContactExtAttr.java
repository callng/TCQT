package com.tencent.qqnt.kernel.nativeinterface;

public final class RecentContactExtAttr {
    public TempChatGameSession gameSession;
    public MsgStatus msgStatus;
    public TempChatServiceAssistantSession serviceAssistantSession;

    public RecentContactExtAttr() {
    }

    public TempChatGameSession getGameSession() {
        return this.gameSession;
    }

    public MsgStatus getMsgStatus() {
        return this.msgStatus;
    }

    public TempChatServiceAssistantSession getServiceAssistantSession() {
        return this.serviceAssistantSession;
    }

    public RecentContactExtAttr(TempChatGameSession tempChatGameSession, TempChatServiceAssistantSession tempChatServiceAssistantSession, MsgStatus msgStatus) {
        this.gameSession = tempChatGameSession;
        this.serviceAssistantSession = tempChatServiceAssistantSession;
        this.msgStatus = msgStatus;
    }
}
