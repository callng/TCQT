package com.tencent.mobileqq.data.troop;

import com.tencent.qqnt.kernel.nativeinterface.GroupAuthInfo;
import com.tencent.qqnt.kernel.nativeinterface.GroupExt;
import com.tencent.qqnt.kernel.nativeinterface.GroupGameList;
import com.tencent.qqnt.kernel.nativeinterface.GroupMsgMask;
import com.tencent.qqnt.kernel.nativeinterface.GroupPermissions;
import com.tencent.qqnt.kernel.nativeinterface.GroupStatus;
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TroopInfo {
    @Deprecated
    public String Administrator;
    public int activeMemberNum;
    public int allowMemberAtAll;
    public int allowMemberKick;
    public long appealDeadline;
    public long associatePubAccount;
    public short cGroupOption;
    public int cityId;
    public long cmdUinFlagEx2;
    public long cmduinFlagEx3Grocery;
    public long discussMaxSeq;
    public long discussToTroopTime;
    public String discussUin;
    public long dwAppPrivilegeFlag;
    public long dwAuthGroupType;
    public long dwCmdUinJoinTime;
    public long dwGagTimeStamp;
    public long dwGagTimeStamp_me;
    public long dwGroupClassExt;
    public long dwGroupFlag;
    public long dwGroupFlagExt;
    public long dwGroupFlagExt3;
    public long dwGroupInfoSeq;
    public long dwGroupLevelSeq;
    public int exitTroopReason;
    public TroopExtDBInfo extDBInfo;
    public String fingertroopmemo;
    public int grade;
    public long groupAllianceid;
    public GroupAuthInfo groupAuthInfo;
    public ArrayList<String> groupCardPrefix;
    public String groupCardPrefixIntro;
    public transient GroupExt groupExt;
    public int groupFlagExt4;
    public int groupFreezeReason;
    public transient GroupGameList groupGameList;
    public GroupAuthInfo groupOwnerAuthInfo;
    public GroupPermissions groupPermissions;
    public GroupStatus groupStatus;
    public boolean hadInitDetail;
    public boolean hadInitExt;
    public boolean hasSetNewTroopHead;
    public boolean hasSetNewTroopName;
    public long hlGuildAppid;
    public int hlGuildBinary;
    public int hlGuildOrgid;
    public long hlGuildSubType;
    public int inviteNoAuthLimitNum;
    public int isAllowHistoryMsgFlag;
    public boolean isExitByJce;
    public boolean isNewTroop;
    public boolean isTop;
    public boolean isTroopBlocked;
    public String joinTroopAnswer;
    public String joinTroopQuestion;
    public int joinTroopSeq;
    public long lastMsgTime;
    public long lastTroopPicSeq;
    public String location;
    private volatile ConcurrentHashMap<Integer, String> mCachedNewLevelMap;
    public boolean mCanSearchByKeywords;
    public boolean mCanSearchByTroopUin;
    public String mGroupClassExtText;
    public int mIsFreezed;
    public long mMemberCardSeq;
    public boolean mMemberInvitingFlag;
    public long mMemberNickIconSeq;
    public long mMemberNumSeq;
    public String mRichFingerMemo;
    public String mTags;

    @Deprecated(since = "付费群下架")
    public float mTroopNeedPayNumber;
    public List<TroopClipPic> mTroopPicList;
    public Set<String> mTroopVerifyingPics;
    public int maxAdminNum;
    public int maxInviteMemNum;
    public MemberRole memberRole;
    public int nMsgLimitFreq;
    public int nTroopGrade;
    public String school;
    public ArrayList<Integer> selectedGameId;
    public long setTopTime;
    public String strLocation;
    public String troopAuthenticateInfo;
    public long troopCreateTime;
    public long troopCreditLevel;
    public int troopLat;
    public int troopLon;
    public String troopNameFromNT;
    public String troopOwnerUid;
    public long troopPrivilegeFlag;
    public String troopRemark;
    public int troopTypeExt;

    @Deprecated
    public String troopcode;
    public short troopface;
    public GroupMsgMask troopmask;
    public String troopmemo;

    @Deprecated(since = "用的地方太多，先保留字段，新需求統一使用troopNameFromNT")
    public String troopname;
    public String troopowneruin;
    public String troopuin;
    public long udwCmdUinRingtoneID;
    public int wMemberMax;
    public int wMemberNum;

    public long getBlockExpireTime() {
        throw new RuntimeException("Stub!");
    }

    public int getBlockType() {
        throw new RuntimeException("Stub!");
    }

    public String getNewTroopNameOrTroopName() {
        throw new RuntimeException("Stub!");
    }

    public String getTroopDisplayName() {
        throw new RuntimeException("Stub!");
    }

    public long getTroopGuildId() {
        throw new RuntimeException("Stub!");
    }

    public String getTroopUin() {
        throw new RuntimeException("Stub!");
    }

    public boolean hadJoinTroop() {
        GroupStatus groupStatus = this.groupStatus;
        return groupStatus != null && groupStatus != GroupStatus.KDELETE && isMember();
    }

    public String getUniqueBlockFlag() {
        return this.isTroopBlocked + "-" + getBlockType() + "-" + getBlockExpireTime() + "-" + hadJoinTroop();
    }

    public int getMemberNumClient() {
        throw new RuntimeException("Stub!");
    }

    public int getMemberNum() {
        return this.wMemberNum;
    }

    public boolean isMemberCountHitTheLimit() {
        return getMemberNum() >= this.wMemberMax;
    }

    public boolean isNeedClearAutoApproval() {
        return this.cGroupOption != 2 || (this.troopPrivilegeFlag & 512) == 512;
    }

    public boolean isNeedInterceptOnBlockTroop() {
        return this.isTroopBlocked && hadJoinTroop();
    }

    public boolean isNewTroop() {
        return this.isNewTroop;
    }

    public boolean isTroopAdmin(String str) {
        throw new RuntimeException("Stub!");
    }

    public boolean isOwner() {
        throw new RuntimeException("Stub!");
    }

    public boolean isAdmin() {
        throw new RuntimeException("Stub!");
    }

    public boolean isMember() {
        MemberRole memberRole = this.memberRole;
        return memberRole == MemberRole.MEMBER || memberRole == MemberRole.ADMIN || memberRole == MemberRole.OWNER;
    }

    public boolean isOwnerOrAdmin() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTroopOwner(String str) {
        throw new RuntimeException("Stub!");
    }

    public boolean isTroopGuild() {
        throw new RuntimeException("Stub!");
    }

    public boolean canModifyTroopName() {
        throw new RuntimeException("Stub!");
    }

    public boolean isPassiveExit() {
        throw new RuntimeException("Stub!");
    }

    public boolean isDisband() {
        throw new RuntimeException("Stub!");
    }

    public boolean isExited() {
        throw new RuntimeException("Stub!");
    }

    public boolean isKicked() {
        throw new RuntimeException("Stub!");
    }

    public TroopInfo(String str) {
        this.extDBInfo.troopUin = str;
    }
}
