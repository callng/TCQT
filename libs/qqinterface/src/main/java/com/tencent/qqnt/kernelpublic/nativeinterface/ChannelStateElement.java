package com.tencent.qqnt.kernelpublic.nativeinterface;

import java.io.Serializable;
import java.util.ArrayList;

public final class ChannelStateElement implements Serializable {
    public ArrayList<ChannStateItemInfo> channStateList;
    public long channelId;
    public int channelState;
    public long channelStateReq;
    public String firstMemberDisplayName;
    public long firstMemberTinyid;
    public long guildId;
    public int guildState;
    public int memberCount;
    public int memberMax;
    public long roomId;
    public String roomTitle;
    long serialVersionUID;
    public long updateTime;

    public ChannelStateElement() {
        this.serialVersionUID = 1L;
        this.firstMemberDisplayName = "";
        this.roomTitle = "";
        this.channStateList = new ArrayList<>();
    }

    public ArrayList<ChannStateItemInfo> getChannStateList() {
        return this.channStateList;
    }

    public long getChannelId() {
        return this.channelId;
    }

    public int getChannelState() {
        return this.channelState;
    }

    public long getChannelStateReq() {
        return this.channelStateReq;
    }

    public String getFirstMemberDisplayName() {
        return this.firstMemberDisplayName;
    }

    public long getFirstMemberTinyid() {
        return this.firstMemberTinyid;
    }

    public long getGuildId() {
        return this.guildId;
    }

    public int getGuildState() {
        return this.guildState;
    }

    public int getMemberCount() {
        return this.memberCount;
    }

    public int getMemberMax() {
        return this.memberMax;
    }

    public long getRoomId() {
        return this.roomId;
    }

    public String getRoomTitle() {
        return this.roomTitle;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public ChannelStateElement(long j, long j2, int i, int i2, long j3, String str, int i3, int i4, long j4, long j5, long j6, String str2, ArrayList<ChannStateItemInfo> arrayList) {
        this.serialVersionUID = 1L;
        this.firstMemberDisplayName = "";
        this.roomTitle = "";
        this.guildId = j;
        this.channelId = j2;
        this.memberCount = i;
        this.memberMax = i2;
        this.firstMemberTinyid = j3;
        this.firstMemberDisplayName = str;
        this.guildState = i3;
        this.channelState = i4;
        this.channelStateReq = j4;
        this.updateTime = j5;
        this.roomId = j6;
        this.roomTitle = str2;
        this.channStateList = arrayList;
    }
}
