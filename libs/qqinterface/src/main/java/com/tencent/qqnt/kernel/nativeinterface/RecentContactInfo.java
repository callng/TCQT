package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.MsgAbstractElement;
import java.util.ArrayList;
import java.util.HashMap;

public final class RecentContactInfo implements IKernelModel {
    public ArrayList<MsgAbstractElement> abstractContent;
    public Integer anonymousFlag;
    public int atType;
    public String avatarPath;
    public String avatarUrl;
    public BotCommonInfo botCommonInfo;
    public long c2cClientMsgSeq;
    public RCChatInfo chatInfo;
    public int chatType;
    public long contactId;
    public ArrayList<MsgAbstractElement> draft;
    public byte draftFlag;
    public long draftTime;
    public ArrayList<RecentContactExtAttr> extAttrs;
    public byte[] extBuffer;
    public GuildContactInfo guildContactInfo;
    public int hiddenFlag;
    public String id;
    public boolean isBeat;
    public boolean isBlock;
    public boolean isMsgDisturb;
    public boolean isOnlineMsg;
    public int keepHiddenFlag;
    public ArrayList<SpecificEventTypeInfoInMsgBox> listOfSpecificEventTypeInfosInMsgBox;
    public HashMap<LiteBusinessType, String> liteBusiness;
    public String memberName;
    public long msgId;
    public long msgRandom;
    public long msgSeq;
    public long msgTime;
    public long msgUid;
    public Long msgUniqueId;
    public ArrayList<RecentContactInfo> nestedChangedList;
    public ArrayList<Long> nestedSortedContactList;
    public int notifiedType;
    public String peerName;
    public String peerUid;
    public long peerUin;
    public String remark;
    public String sendMemberName;
    public String sendNickName;
    public String sendRemarkName;
    public int sendStatus;
    public RCSenderInfo senderInfo;
    public String senderUid;
    public long senderUin;
    public int sessionType;
    public long shieldFlag;
    public long sortField;
    public byte specialCareFlag;
    public byte topFlag;
    public long topFlagTime;
    public int unreadChatCnt;
    public long unreadCnt;
    public long unreadFlag;
    public VASRecentContactMsgElement vasMsgInfo;
    public VASPersonalElement vasPersonalInfo;

    public RecentContactInfo() {
        this.id = "";
        this.senderUid = "";
        this.peerUid = "";
        this.sendRemarkName = "";
        this.sendMemberName = "";
        this.sendNickName = "";
        this.peerName = "";
        this.remark = "";
        this.avatarUrl = "";
        this.avatarPath = "";
        this.abstractContent = new ArrayList<>();
        this.draft = new ArrayList<>();
        this.nestedSortedContactList = new ArrayList<>();
        this.nestedChangedList = new ArrayList<>();
        this.vasPersonalInfo = new VASPersonalElement();
        this.vasMsgInfo = new VASRecentContactMsgElement();
        this.extAttrs = new ArrayList<>();
        this.liteBusiness = new HashMap<>();
        this.chatInfo = new RCChatInfo();
        this.senderInfo = new RCSenderInfo();
    }

    public ArrayList<MsgAbstractElement> getAbstractContent() {
        return this.abstractContent;
    }

    public Integer getAnonymousFlag() {
        return this.anonymousFlag;
    }

    public int getAtType() {
        return this.atType;
    }

    public String getAvatarPath() {
        return this.avatarPath;
    }

    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    public BotCommonInfo getBotCommonInfo() {
        return this.botCommonInfo;
    }

    public long getC2cClientMsgSeq() {
        return this.c2cClientMsgSeq;
    }

    public RCChatInfo getChatInfo() {
        return this.chatInfo;
    }

    public int getChatType() {
        return this.chatType;
    }

    public long getContactId() {
        return this.contactId;
    }

    public ArrayList<MsgAbstractElement> getDraft() {
        return this.draft;
    }

    public byte getDraftFlag() {
        return this.draftFlag;
    }

    public long getDraftTime() {
        return this.draftTime;
    }

    public ArrayList<RecentContactExtAttr> getExtAttrs() {
        return this.extAttrs;
    }

    public byte[] getExtBuffer() {
        return this.extBuffer;
    }

    public GuildContactInfo getGuildContactInfo() {
        return this.guildContactInfo;
    }

    public int getHiddenFlag() {
        return this.hiddenFlag;
    }

    public String getId() {
        return this.id;
    }

    public boolean getIsBeat() {
        return this.isBeat;
    }

    public boolean getIsBlock() {
        return this.isBlock;
    }

    public boolean getIsMsgDisturb() {
        return this.isMsgDisturb;
    }

    public boolean getIsOnlineMsg() {
        return this.isOnlineMsg;
    }

    public int getKeepHiddenFlag() {
        return this.keepHiddenFlag;
    }

    public ArrayList<SpecificEventTypeInfoInMsgBox> getListOfSpecificEventTypeInfosInMsgBox() {
        return this.listOfSpecificEventTypeInfosInMsgBox;
    }

    public HashMap<LiteBusinessType, String> getLiteBusiness() {
        return this.liteBusiness;
    }

    public String getMemberName() {
        return this.memberName;
    }

    public long getMsgId() {
        return this.msgId;
    }

    public long getMsgRandom() {
        return this.msgRandom;
    }

    public long getMsgSeq() {
        return this.msgSeq;
    }

    public long getMsgTime() {
        return this.msgTime;
    }

    public long getMsgUid() {
        return this.msgUid;
    }

    public Long getMsgUniqueId() {
        return this.msgUniqueId;
    }

    public ArrayList<RecentContactInfo> getNestedChangedList() {
        return this.nestedChangedList;
    }

    public ArrayList<Long> getNestedSortedContactList() {
        return this.nestedSortedContactList;
    }

    public int getNotifiedType() {
        return this.notifiedType;
    }

    public String getPeerName() {
        return this.peerName;
    }

    public String getPeerUid() {
        return this.peerUid;
    }

    public long getPeerUin() {
        return this.peerUin;
    }

    public String getRemark() {
        return this.remark;
    }

    public String getSendMemberName() {
        return this.sendMemberName;
    }

    public String getSendNickName() {
        return this.sendNickName;
    }

    public String getSendRemarkName() {
        return this.sendRemarkName;
    }

    public int getSendStatus() {
        return this.sendStatus;
    }

    public RCSenderInfo getSenderInfo() {
        return this.senderInfo;
    }

    public String getSenderUid() {
        return this.senderUid;
    }

    public long getSenderUin() {
        return this.senderUin;
    }

    public int getSessionType() {
        return this.sessionType;
    }

    public long getShieldFlag() {
        return this.shieldFlag;
    }

    public long getSortField() {
        return this.sortField;
    }

    public byte getSpecialCareFlag() {
        return this.specialCareFlag;
    }

    public byte getTopFlag() {
        return this.topFlag;
    }

    public long getTopFlagTime() {
        return this.topFlagTime;
    }

    public int getUnreadChatCnt() {
        return this.unreadChatCnt;
    }

    public long getUnreadCnt() {
        return this.unreadCnt;
    }

    public long getUnreadFlag() {
        return this.unreadFlag;
    }

    public VASRecentContactMsgElement getVasMsgInfo() {
        return this.vasMsgInfo;
    }

    public VASPersonalElement getVasPersonalInfo() {
        return this.vasPersonalInfo;
    }

    public void setAbstractContent(ArrayList<MsgAbstractElement> arrayList) {
        this.abstractContent = arrayList;
    }

    public void setAnonymousFlag(Integer num) {
        this.anonymousFlag = num;
    }

    public void setAtType(int i) {
        this.atType = i;
    }

    public void setAvatarPath(String str) {
        this.avatarPath = str;
    }

    public void setAvatarUrl(String str) {
        this.avatarUrl = str;
    }

    public void setBotCommonInfo(BotCommonInfo botCommonInfo) {
        this.botCommonInfo = botCommonInfo;
    }

    public void setC2cClientMsgSeq(long j) {
        this.c2cClientMsgSeq = j;
    }

    public void setChatInfo(RCChatInfo rCChatInfo) {
        this.chatInfo = rCChatInfo;
    }

    public void setChatType(int i) {
        this.chatType = i;
    }

    public void setContactId(long j) {
        this.contactId = j;
    }

    public void setDraft(ArrayList<MsgAbstractElement> arrayList) {
        this.draft = arrayList;
    }

    public void setDraftFlag(byte b) {
        this.draftFlag = b;
    }

    public void setDraftTime(long j) {
        this.draftTime = j;
    }

    public void setExtAttrs(ArrayList<RecentContactExtAttr> arrayList) {
        this.extAttrs = arrayList;
    }

    public void setExtBuffer(byte[] bArr) {
        this.extBuffer = bArr;
    }

    public void setGuildContactInfo(GuildContactInfo guildContactInfo) {
        this.guildContactInfo = guildContactInfo;
    }

    public void setHiddenFlag(int i) {
        this.hiddenFlag = i;
    }

    public void setId(String str) {
        this.id = str;
    }

    public void setIsBeat(boolean z) {
        this.isBeat = z;
    }

    public void setIsBlock(boolean z) {
        this.isBlock = z;
    }

    public void setIsMsgDisturb(boolean z) {
        this.isMsgDisturb = z;
    }

    public void setIsOnlineMsg(boolean z) {
        this.isOnlineMsg = z;
    }

    public void setKeepHiddenFlag(int i) {
        this.keepHiddenFlag = i;
    }

    public void setListOfSpecificEventTypeInfosInMsgBox(ArrayList<SpecificEventTypeInfoInMsgBox> arrayList) {
        this.listOfSpecificEventTypeInfosInMsgBox = arrayList;
    }

    public void setLiteBusiness(HashMap<LiteBusinessType, String> hashMap) {
        this.liteBusiness = hashMap;
    }

    public void setMemberName(String str) {
        this.memberName = str;
    }

    public void setMsgId(long j) {
        this.msgId = j;
    }

    public void setMsgRandom(long j) {
        this.msgRandom = j;
    }

    public void setMsgSeq(long j) {
        this.msgSeq = j;
    }

    public void setMsgTime(long j) {
        this.msgTime = j;
    }

    public void setMsgUid(long j) {
        this.msgUid = j;
    }

    public void setMsgUniqueId(Long l) {
        this.msgUniqueId = l;
    }

    public void setNestedChangedList(ArrayList<RecentContactInfo> arrayList) {
        this.nestedChangedList = arrayList;
    }

    public void setNestedSortedContactList(ArrayList<Long> arrayList) {
        this.nestedSortedContactList = arrayList;
    }

    public void setNotifiedType(int i) {
        this.notifiedType = i;
    }

    public void setPeerName(String str) {
        this.peerName = str;
    }

    public void setPeerUid(String str) {
        this.peerUid = str;
    }

    public void setPeerUin(long j) {
        this.peerUin = j;
    }

    public void setRemark(String str) {
        this.remark = str;
    }

    public void setSendMemberName(String str) {
        this.sendMemberName = str;
    }

    public void setSendNickName(String str) {
        this.sendNickName = str;
    }

    public void setSendRemarkName(String str) {
        this.sendRemarkName = str;
    }

    public void setSendStatus(int i) {
        this.sendStatus = i;
    }

    public void setSenderInfo(RCSenderInfo rCSenderInfo) {
        this.senderInfo = rCSenderInfo;
    }

    public void setSenderUid(String str) {
        this.senderUid = str;
    }

    public void setSenderUin(long j) {
        this.senderUin = j;
    }

    public void setSessionType(int i) {
        this.sessionType = i;
    }

    public void setShieldFlag(long j) {
        this.shieldFlag = j;
    }

    public void setSortField(long j) {
        this.sortField = j;
    }

    public void setSpecialCareFlag(byte b) {
        this.specialCareFlag = b;
    }

    public void setTopFlag(byte b) {
        this.topFlag = b;
    }

    public void setTopFlagTime(long j) {
        this.topFlagTime = j;
    }

    public void setUnreadChatCnt(int i) {
        this.unreadChatCnt = i;
    }

    public void setUnreadCnt(long j) {
        this.unreadCnt = j;
    }

    public void setUnreadFlag(long j) {
        this.unreadFlag = j;
    }

    public void setVasMsgInfo(VASRecentContactMsgElement vASRecentContactMsgElement) {
        this.vasMsgInfo = vASRecentContactMsgElement;
    }

    public void setVasPersonalInfo(VASPersonalElement vASPersonalElement) {
        this.vasPersonalInfo = vASPersonalElement;
    }

    public RecentContactInfo(String str, long j, long j2, int i, String str2, long j3, String str3, long j4, long j5, long j6, long j7, long j8, long j9, String str4, String str5, String str6, String str7, String str8, String str9, String str10, String str11, ArrayList<MsgAbstractElement> arrayList, int i2, byte b, long j10, byte b2, long j11, byte b3, int i3, long j12, int i4, ArrayList<MsgAbstractElement> arrayList2, int i5, int i6, boolean z, ArrayList<Long> arrayList3, ArrayList<RecentContactInfo> arrayList4, long j13, int i7, long j14, boolean z2, boolean z3, long j15, int i8, boolean z4, ArrayList<SpecificEventTypeInfoInMsgBox> arrayList5, GuildContactInfo guildContactInfo, VASPersonalElement vASPersonalElement, VASRecentContactMsgElement vASRecentContactMsgElement, Integer num, byte[] bArr, ArrayList<RecentContactExtAttr> arrayList6, HashMap<LiteBusinessType, String> hashMap) {
        this.id = "";
        this.senderUid = "";
        this.peerUid = "";
        this.sendRemarkName = "";
        this.sendMemberName = "";
        this.sendNickName = "";
        this.peerName = "";
        this.remark = "";
        this.avatarUrl = "";
        this.avatarPath = "";
        this.abstractContent = new ArrayList<>();
        this.draft = new ArrayList<>();
        this.nestedSortedContactList = new ArrayList<>();
        this.nestedChangedList = new ArrayList<>();
        this.vasPersonalInfo = new VASPersonalElement();
        this.vasMsgInfo = new VASRecentContactMsgElement();
        this.extAttrs = new ArrayList<>();
        this.liteBusiness = new HashMap<>();
        this.chatInfo = new RCChatInfo();
        this.senderInfo = new RCSenderInfo();
        this.id = str;
        this.contactId = j;
        this.sortField = j2;
        this.chatType = i;
        this.senderUid = str2;
        this.senderUin = j3;
        this.peerUid = str3;
        this.peerUin = j4;
        this.msgSeq = j5;
        this.c2cClientMsgSeq = j6;
        this.msgUid = j7;
        this.msgRandom = j8;
        this.msgTime = j9;
        this.sendRemarkName = str4;
        this.sendMemberName = str5;
        this.sendNickName = str6;
        this.peerName = str7;
        this.remark = str8;
        this.memberName = str9;
        this.avatarUrl = str10;
        this.avatarPath = str11;
        this.abstractContent = arrayList;
        this.sendStatus = i2;
        this.topFlag = b;
        this.topFlagTime = j10;
        this.draftFlag = b2;
        this.draftTime = j11;
        this.specialCareFlag = b3;
        this.sessionType = i3;
        this.shieldFlag = j12;
        this.atType = i4;
        this.draft = arrayList2;
        this.hiddenFlag = i5;
        this.keepHiddenFlag = i6;
        this.isMsgDisturb = z;
        this.nestedSortedContactList = arrayList3;
        this.nestedChangedList = arrayList4;
        this.unreadCnt = j13;
        this.unreadChatCnt = i7;
        this.unreadFlag = j14;
        this.isBeat = z2;
        this.isOnlineMsg = z3;
        this.msgId = j15;
        this.notifiedType = i8;
        this.isBlock = z4;
        this.listOfSpecificEventTypeInfosInMsgBox = arrayList5;
        this.guildContactInfo = guildContactInfo;
        this.vasPersonalInfo = vASPersonalElement;
        this.vasMsgInfo = vASRecentContactMsgElement;
        this.anonymousFlag = num;
        this.extBuffer = bArr;
        this.extAttrs = arrayList6;
        this.liteBusiness = hashMap;
    }
}
