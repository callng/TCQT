package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupMemberNewExtInfo {
    public long groupCode;
    public long groupStatusBarSeq;
    public GroupAuthInfo identifier = new GroupAuthInfo();
    public int joinGroupSeq;
    public long memberExtInfoSeq;
    public int notAllowHistoryMsg;
    public int onlineStatusSwitch;
    public int showAvatarSwitch;
    public long topBannerSeq;
    public long uin;
    public int wechatFocusFlag;

    public long getGroupCode() {
        return this.groupCode;
    }

    public long getGroupStatusBarSeq() {
        return this.groupStatusBarSeq;
    }

    public GroupAuthInfo getIdentifier() {
        return this.identifier;
    }

    public int getJoinGroupSeq() {
        return this.joinGroupSeq;
    }

    public long getMemberExtInfoSeq() {
        return this.memberExtInfoSeq;
    }

    public int getNotAllowHistoryMsg() {
        return this.notAllowHistoryMsg;
    }

    public int getOnlineStatusSwitch() {
        return this.onlineStatusSwitch;
    }

    public int getShowAvatarSwitch() {
        return this.showAvatarSwitch;
    }

    public long getTopBannerSeq() {
        return this.topBannerSeq;
    }

    public long getUin() {
        return this.uin;
    }

    public int getWechatFocusFlag() {
        return this.wechatFocusFlag;
    }
}
