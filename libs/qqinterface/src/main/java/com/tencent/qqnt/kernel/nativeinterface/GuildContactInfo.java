package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class GuildContactInfo {
    public ArrayList<GuildAbstractPiece> abstractList;
    public int activityType;
    public long avatarSeq;
    public boolean channelHighLight;
    public String channelId;
    public String channelName;
    public int channelType;
    public String feedId;
    public GuildAbstractType grayAbstractType;
    public String guildId;
    public String guildName;
    public boolean isEntered;
    public boolean isShowChannelName;
    public boolean isSticky;
    public long msgSeq;
    public long msgType;
    public int newPostUnreadCnt;
    public int nodeSubType;
    public GuildAbstractType redAbstractType;
    public String schema;
    public byte[] tocken;
    public int unNotifyFlag;
    public UnreadCntInfo unreadCntInfo;

    public GuildContactInfo() {
        this.guildId = "";
        this.channelId = "";
        this.unreadCntInfo = new UnreadCntInfo();
        this.guildName = "";
        this.channelName = "";
        this.grayAbstractType = new GuildAbstractType();
        this.schema = "";
        this.redAbstractType = new GuildAbstractType();
        this.abstractList = new ArrayList<>();
    }

    public ArrayList<GuildAbstractPiece> getAbstractList() {
        return this.abstractList;
    }

    public int getActivityType() {
        return this.activityType;
    }

    public long getAvatarSeq() {
        return this.avatarSeq;
    }

    public boolean getChannelHighLight() {
        return this.channelHighLight;
    }

    public String getChannelId() {
        return this.channelId;
    }

    public String getChannelName() {
        return this.channelName;
    }

    public int getChannelType() {
        return this.channelType;
    }

    public String getFeedId() {
        return this.feedId;
    }

    public GuildAbstractType getGrayAbstractType() {
        return this.grayAbstractType;
    }

    public String getGuildId() {
        return this.guildId;
    }

    public String getGuildName() {
        return this.guildName;
    }

    public boolean getIsEntered() {
        return this.isEntered;
    }

    public boolean getIsShowChannelName() {
        return this.isShowChannelName;
    }

    public boolean getIsSticky() {
        return this.isSticky;
    }

    public long getMsgSeq() {
        return this.msgSeq;
    }

    public long getMsgType() {
        return this.msgType;
    }

    public int getNewPostUnreadCnt() {
        return this.newPostUnreadCnt;
    }

    public int getNodeSubType() {
        return this.nodeSubType;
    }

    public GuildAbstractType getRedAbstractType() {
        return this.redAbstractType;
    }

    public String getSchema() {
        return this.schema;
    }

    public byte[] getTocken() {
        return this.tocken;
    }

    public int getUnNotifyFlag() {
        return this.unNotifyFlag;
    }

    public UnreadCntInfo getUnreadCntInfo() {
        return this.unreadCntInfo;
    }

    public GuildContactInfo(String str, String str2, UnreadCntInfo unreadCntInfo, String str3, String str4, int i, int i2, int i3, boolean z, long j, long j2, long j3, boolean z2, boolean z3, boolean z4, String str5) {
        this.guildId = "";
        this.channelId = "";
        this.unreadCntInfo = new UnreadCntInfo();
        this.guildName = "";
        this.channelName = "";
        this.grayAbstractType = new GuildAbstractType();
        this.schema = "";
        this.redAbstractType = new GuildAbstractType();
        this.abstractList = new ArrayList<>();
        this.guildId = str;
        this.channelId = str2;
        this.unreadCntInfo = unreadCntInfo;
        this.guildName = str3;
        this.channelName = str4;
        this.channelType = i;
        this.nodeSubType = i2;
        this.activityType = i3;
        this.channelHighLight = z;
        this.avatarSeq = j;
        this.msgType = j2;
        this.msgSeq = j3;
        this.isSticky = z2;
        this.isEntered = z3;
        this.isShowChannelName = z4;
        this.feedId = str5;
    }
}
