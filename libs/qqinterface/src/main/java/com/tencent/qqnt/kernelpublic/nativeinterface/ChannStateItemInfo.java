package com.tencent.qqnt.kernelpublic.nativeinterface;

import java.io.Serializable;

public final class ChannStateItemInfo implements Serializable {
    public int channelState;
    public String channelStateStr;
    public int priority;
    long serialVersionUID;
    public long updateTs;

    public ChannStateItemInfo() {
        this.serialVersionUID = 1L;
        this.channelStateStr = "";
    }

    public int getChannelState() {
        return this.channelState;
    }

    public String getChannelStateStr() {
        return this.channelStateStr;
    }

    public int getPriority() {
        return this.priority;
    }

    public long getUpdateTs() {
        return this.updateTs;
    }

    public ChannStateItemInfo(int i, int i2, long j, String str) {
        this.serialVersionUID = 1L;
        this.channelState = i;
        this.priority = i2;
        this.updateTs = j;
        this.channelStateStr = str;
    }
}
