package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupExt {
    public int aiChatAuthorizeSwitch;
    public int appIdentifierSwitch;
    public int appIdentifierType;
    public long bindGuildId;
    public int blacklistExpireTime;
    public int companyId;
    public int cronMuteTaskSwitch;
    public long dragonLadderSeq;
    public int essentialMsgPrivilege;
    public int essentialMsgSwitch;
    public long fullGroupExpansionSeq;
    public int fullGroupExpansionSwitch;
    public long gangUpId;
    public long groupAioBindGuildId;
    public int groupBindGuildSwitch;
    public int groupCardGameInfoSwitch;
    public long groupFlagPro1;
    public int groupInfoExtSeq;
    public long groupNormalTopBannerSeq;
    public int groupSecurityBannedFlag;
    public int groupSquareSwitch;
    public long groupStatusBarSeq;
    public int hasGroupCustomPortrait;
    public int inviteRobotMemberExamine;
    public int inviteRobotMemberSwitch;
    public int inviteRobotSwitch;
    public int isLimitGroupRtc;
    public int joinAutoApproveSwitch;
    public int lightCharNum;
    public long luckyWordId;
    public int memberChangeGroupNameSwitch;
    public long msgEventSeq;
    public int qqMusicMedalSwitch;
    public int reserve;
    public int showPlayTogetherSwitch;
    public int starId;
    public int todoSeq;
    public long topBannerSeq;
    public long viewedMsgDisappearTime;
    public String luckyWord = "";
    public MemberID groupOwnerId = new MemberID();
    public GuildIdList groupBindGuildIds = new GuildIdList();
    public GroupExtFlameData groupExtFlameData = new GroupExtFlameData();
    public GuildIdList groupExcludeGuildIds = new GuildIdList();
    public GroupExtMedalData groupExtMedalData = new GroupExtMedalData();
    public GroupExtInfoMuteTask cronMuteTask = new GroupExtInfoMuteTask();
    public GroupAuthInfo groupIdentifier = new GroupAuthInfo();
    public JoinGroupAuthValue joinAutoApproveValue = new JoinGroupAuthValue();
    public GroupAiAssistantSwitchStore groupAiAssistantSwitchStore = new GroupAiAssistantSwitchStore();
    public CertifiedGroupAnalysisSwitchStore certifiedGroupAnalysis = new CertifiedGroupAnalysisSwitchStore();

    public int getAiChatAuthorizeSwitch() {
        return this.aiChatAuthorizeSwitch;
    }

    public int getAppIdentifierSwitch() {
        return this.appIdentifierSwitch;
    }

    public int getAppIdentifierType() {
        return this.appIdentifierType;
    }

    public long getBindGuildId() {
        return this.bindGuildId;
    }

    public int getBlacklistExpireTime() {
        return this.blacklistExpireTime;
    }

    public CertifiedGroupAnalysisSwitchStore getCertifiedGroupAnalysis() {
        return this.certifiedGroupAnalysis;
    }

    public int getCompanyId() {
        return this.companyId;
    }

    public GroupExtInfoMuteTask getCronMuteTask() {
        return this.cronMuteTask;
    }

    public int getCronMuteTaskSwitch() {
        return this.cronMuteTaskSwitch;
    }

    public long getDragonLadderSeq() {
        return this.dragonLadderSeq;
    }

    public int getEssentialMsgPrivilege() {
        return this.essentialMsgPrivilege;
    }

    public int getEssentialMsgSwitch() {
        return this.essentialMsgSwitch;
    }

    public long getFullGroupExpansionSeq() {
        return this.fullGroupExpansionSeq;
    }

    public int getFullGroupExpansionSwitch() {
        return this.fullGroupExpansionSwitch;
    }

    public long getGangUpId() {
        return this.gangUpId;
    }

    public GroupAiAssistantSwitchStore getGroupAiAssistantSwitchStore() {
        return this.groupAiAssistantSwitchStore;
    }

    public long getGroupAioBindGuildId() {
        return this.groupAioBindGuildId;
    }

    public GuildIdList getGroupBindGuildIds() {
        return this.groupBindGuildIds;
    }

    public int getGroupBindGuildSwitch() {
        return this.groupBindGuildSwitch;
    }

    public int getGroupCardGameInfoSwitch() {
        return this.groupCardGameInfoSwitch;
    }

    public GuildIdList getGroupExcludeGuildIds() {
        return this.groupExcludeGuildIds;
    }

    public GroupExtFlameData getGroupExtFlameData() {
        return this.groupExtFlameData;
    }

    public GroupExtMedalData getGroupExtMedalData() {
        return this.groupExtMedalData;
    }

    public long getGroupFlagPro1() {
        return this.groupFlagPro1;
    }

    public GroupAuthInfo getGroupIdentifier() {
        return this.groupIdentifier;
    }

    public int getGroupInfoExtSeq() {
        return this.groupInfoExtSeq;
    }

    public long getGroupNormalTopBannerSeq() {
        return this.groupNormalTopBannerSeq;
    }

    public MemberID getGroupOwnerId() {
        return this.groupOwnerId;
    }

    public int getGroupSecurityBannedFlag() {
        return this.groupSecurityBannedFlag;
    }

    public int getGroupSquareSwitch() {
        return this.groupSquareSwitch;
    }

    public long getGroupStatusBarSeq() {
        return this.groupStatusBarSeq;
    }

    public int getHasGroupCustomPortrait() {
        return this.hasGroupCustomPortrait;
    }

    public int getInviteRobotMemberExamine() {
        return this.inviteRobotMemberExamine;
    }

    public int getInviteRobotMemberSwitch() {
        return this.inviteRobotMemberSwitch;
    }

    public int getInviteRobotSwitch() {
        return this.inviteRobotSwitch;
    }

    public int getIsLimitGroupRtc() {
        return this.isLimitGroupRtc;
    }

    public int getJoinAutoApproveSwitch() {
        return this.joinAutoApproveSwitch;
    }

    public JoinGroupAuthValue getJoinAutoApproveValue() {
        return this.joinAutoApproveValue;
    }

    public int getLightCharNum() {
        return this.lightCharNum;
    }

    public String getLuckyWord() {
        return this.luckyWord;
    }

    public long getLuckyWordId() {
        return this.luckyWordId;
    }

    public int getMemberChangeGroupNameSwitch() {
        return this.memberChangeGroupNameSwitch;
    }

    public long getMsgEventSeq() {
        return this.msgEventSeq;
    }

    public int getQqMusicMedalSwitch() {
        return this.qqMusicMedalSwitch;
    }

    public int getReserve() {
        return this.reserve;
    }

    public int getShowPlayTogetherSwitch() {
        return this.showPlayTogetherSwitch;
    }

    public int getStarId() {
        return this.starId;
    }

    public int getTodoSeq() {
        return this.todoSeq;
    }

    public long getTopBannerSeq() {
        return this.topBannerSeq;
    }

    public long getViewedMsgDisappearTime() {
        return this.viewedMsgDisappearTime;
    }
}
