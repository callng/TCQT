package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupAiAssistantSwitchStore {
    public long lastOpTime;
    public GroupAiSubSwitchStore proactiveSpeak = new GroupAiSubSwitchStore();
    public GroupAiSubSwitchStore smartRecommend = new GroupAiSubSwitchStore();
    public int state;

    public long getLastOpTime() {
        return this.lastOpTime;
    }

    public GroupAiSubSwitchStore getProactiveSpeak() {
        return this.proactiveSpeak;
    }

    public GroupAiSubSwitchStore getSmartRecommend() {
        return this.smartRecommend;
    }

    public int getState() {
        return this.state;
    }
}
