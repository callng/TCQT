package com.tencent.qqnt.kernel.nativeinterface;

public final class DirectMsgMember {
    public String memberName;
    public int memberType;
    public int msgNotifyType;
    public String nickName;
    public long srcGuildId;
    public String srcGuildName;
    public long tinyId;

    public DirectMsgMember() {
        this.srcGuildName = "";
        this.nickName = "";
        this.memberName = "";
    }

    public String getMemberName() {
        return this.memberName;
    }

    public int getMemberType() {
        return this.memberType;
    }

    public int getMsgNotifyType() {
        return this.msgNotifyType;
    }

    public String getNickName() {
        return this.nickName;
    }

    public long getSrcGuildId() {
        return this.srcGuildId;
    }

    public String getSrcGuildName() {
        return this.srcGuildName;
    }

    public long getTinyId() {
        return this.tinyId;
    }

    public DirectMsgMember(long j, long j2, String str, String str2, String str3, int i, int i2) {
        this.tinyId = j;
        this.srcGuildId = j2;
        this.srcGuildName = str;
        this.nickName = str2;
        this.memberName = str3;
        this.msgNotifyType = i;
        this.memberType = i2;
    }
}
