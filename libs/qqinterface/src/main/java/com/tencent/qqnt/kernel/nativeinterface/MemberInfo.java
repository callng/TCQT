package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole;
import java.io.Serializable;

public final class MemberInfo implements IKernelModel, Serializable {
    public int bigClubFlag;
    public int bigClubLevel;
    public int cardNameId;
    public int cardType;
    public int creditLevel;
    public int globalGroupLevel;
    public int globalGroupPoint;
    public boolean isDelete;
    public boolean isRobot;
    public boolean isSpecialConcerned;
    public boolean isSpecialShielded;
    public int joinTime;
    public int lastSpeakTime;
    public int memberFlag;
    public int memberFlagExt;
    public int memberFlagExt2;
    public int memberLevel;
    public int memberMobileFlag;
    public int memberRealLevel;
    public int memberTitleId;
    public int mssVipType;
    public int richFlag;
    public int shutUpTime;
    public long specialTitleExpireTime;
    public long uin;
    public int userShowFlag;
    public int userShowFlagNew;
    public String uid = "";
    public String qid = "";
    public String nick = "";
    public String remark = "";
    public String cardName = "";
    public MemberRole role = MemberRole.values()[0];
    public String avatarPath = "";
    public byte[] groupHonor = new byte[0];
    public String memberSpecialTitle = "";
    public String autoRemark = "";

    public String getAutoRemark() {
        return this.autoRemark;
    }

    public String getAvatarPath() {
        return this.avatarPath;
    }

    public int getBigClubFlag() {
        return this.bigClubFlag;
    }

    public int getBigClubLevel() {
        return this.bigClubLevel;
    }

    public String getCardName() {
        return this.cardName;
    }

    public int getCardNameId() {
        return this.cardNameId;
    }

    public int getCardType() {
        return this.cardType;
    }

    public int getCreditLevel() {
        return this.creditLevel;
    }

    public int getGlobalGroupLevel() {
        return this.globalGroupLevel;
    }

    public int getGlobalGroupPoint() {
        return this.globalGroupPoint;
    }

    public byte[] getGroupHonor() {
        return this.groupHonor;
    }

    public boolean getIsDelete() {
        return this.isDelete;
    }

    public boolean getIsRobot() {
        return this.isRobot;
    }

    public boolean getIsSpecialConcerned() {
        return this.isSpecialConcerned;
    }

    public boolean getIsSpecialShielded() {
        return this.isSpecialShielded;
    }

    public int getJoinTime() {
        return this.joinTime;
    }

    public int getLastSpeakTime() {
        return this.lastSpeakTime;
    }

    public int getMemberFlag() {
        return this.memberFlag;
    }

    public int getMemberFlagExt() {
        return this.memberFlagExt;
    }

    public int getMemberFlagExt2() {
        return this.memberFlagExt2;
    }

    public int getMemberLevel() {
        return this.memberLevel;
    }

    public int getMemberMobileFlag() {
        return this.memberMobileFlag;
    }

    public int getMemberRealLevel() {
        return this.memberRealLevel;
    }

    public String getMemberSpecialTitle() {
        return this.memberSpecialTitle;
    }

    public int getMemberTitleId() {
        return this.memberTitleId;
    }

    public int getMssVipType() {
        return this.mssVipType;
    }

    public String getNick() {
        return this.nick;
    }

    public String getQid() {
        return this.qid;
    }

    public String getRemark() {
        return this.remark;
    }

    public int getRichFlag() {
        return this.richFlag;
    }

    public MemberRole getRole() {
        return this.role;
    }

    public int getShutUpTime() {
        return this.shutUpTime;
    }

    public long getSpecialTitleExpireTime() {
        return this.specialTitleExpireTime;
    }

    public String getUid() {
        return this.uid;
    }

    public long getUin() {
        return this.uin;
    }

    public int getUserShowFlag() {
        return this.userShowFlag;
    }

    public int getUserShowFlagNew() {
        return this.userShowFlagNew;
    }

    public void setAutoRemark(String str) {
        this.autoRemark = str;
    }

    public void setAvatarPath(String str) {
        this.avatarPath = str;
    }

    public void setBigClubFlag(int i) {
        this.bigClubFlag = i;
    }

    public void setBigClubLevel(int i) {
        this.bigClubLevel = i;
    }

    public void setCardName(String str) {
        this.cardName = str;
    }

    public void setCardNameId(int i) {
        this.cardNameId = i;
    }

    public void setCardType(int i) {
        this.cardType = i;
    }

    public void setCreditLevel(int i) {
        this.creditLevel = i;
    }

    public void setGlobalGroupLevel(int i) {
        this.globalGroupLevel = i;
    }

    public void setGlobalGroupPoint(int i) {
        this.globalGroupPoint = i;
    }

    public void setGroupHonor(byte[] bArr) {
        this.groupHonor = bArr;
    }

    public void setIsDelete(boolean z) {
        this.isDelete = z;
    }

    public void setIsRobot(boolean z) {
        this.isRobot = z;
    }

    public void setIsSpecialConcerned(boolean z) {
        this.isSpecialConcerned = z;
    }

    public void setIsSpecialShielded(boolean z) {
        this.isSpecialShielded = z;
    }

    public void setJoinTime(int i) {
        this.joinTime = i;
    }

    public void setLastSpeakTime(int i) {
        this.lastSpeakTime = i;
    }

    public void setMemberFlag(int i) {
        this.memberFlag = i;
    }

    public void setMemberFlagExt(int i) {
        this.memberFlagExt = i;
    }

    public void setMemberFlagExt2(int i) {
        this.memberFlagExt2 = i;
    }

    public void setMemberLevel(int i) {
        this.memberLevel = i;
    }

    public void setMemberMobileFlag(int i) {
        this.memberMobileFlag = i;
    }

    public void setMemberRealLevel(int i) {
        this.memberRealLevel = i;
    }

    public void setMemberSpecialTitle(String str) {
        this.memberSpecialTitle = str;
    }

    public void setMemberTitleId(int i) {
        this.memberTitleId = i;
    }

    public void setMssVipType(int i) {
        this.mssVipType = i;
    }

    public void setNick(String str) {
        this.nick = str;
    }

    public void setQid(String str) {
        this.qid = str;
    }

    public void setRemark(String str) {
        this.remark = str;
    }

    public void setRichFlag(int i) {
        this.richFlag = i;
    }

    public void setRole(MemberRole memberRole) {
        this.role = memberRole;
    }

    public void setShutUpTime(int i) {
        this.shutUpTime = i;
    }

    public void setSpecialTitleExpireTime(long j) {
        this.specialTitleExpireTime = j;
    }

    public void setUid(String str) {
        this.uid = str;
    }

    public void setUin(long j) {
        this.uin = j;
    }

    public void setUserShowFlag(int i) {
        this.userShowFlag = i;
    }

    public void setUserShowFlagNew(int i) {
        this.userShowFlagNew = i;
    }
}
