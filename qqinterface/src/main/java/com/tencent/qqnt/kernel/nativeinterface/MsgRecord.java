package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;
import java.util.HashMap;

public final class MsgRecord {
    public AnonymousExtInfo anonymousExtInfo;
    public int atType;
    public ArrayList<MsgElement> auxiliaryElements;
    public int avatarFlag;
    public String avatarMeta;
    public String avatarPendant;
    public int categoryManage;
    public String channelId;
    public String channelName;
    public int chatType;
    public GuildClientIdentity clientIdentityInfo;
    public long clientSeq;
    public long cntSeq;
    public long commentCnt;
    public int directMsgFlag;
    public ArrayList<DirectMsgMember> directMsgMembers;
    public boolean editable;
    public ArrayList<MsgElement> elements;
    public ArrayList<MsgEmojiLikes> emojiLikesList;
    public byte[] extInfoForUI;
    public String feedId;
    public Integer fileGroupSize;
    public FoldingInfo foldingInfo;
    public FreqLimitInfo freqLimitInfo;
    public long fromAppid;
    public FromRoleInfo fromChannelRoleInfo;
    public FromRoleInfo fromGuildRoleInfo;
    public long fromUid;
    public byte[] generalFlags;
    public long guildCode;
    public String guildId;
    public String guildName;
    public boolean isImportMsg;
    public boolean isOnlineMsg;
    public boolean isSupportRoamMsg;
    public FromRoleInfo levelRoleInfo;
    public HashMap<Integer, MsgAttributeInfo> msgAttrs;
    public byte[] msgEventInfo;
    public long msgId;
    public byte[] msgMeta;
    public long msgRandom;
    public long msgSeq;
    public long msgTime;
    public int msgType;
    public MultiTransInfo multiTransInfo;
    public int nameType;
    public String peerName;
    public String peerUid;
    public long peerUin;
    public GuildMedal personalMedal;
    public long recallTime;
    public ArrayList<MsgRecord> records;
    public long roleId;
    public int roleType;
    public String sendMemberName;
    public String sendNickName;
    public String sendRemarkName;
    public int sendStatus;
    public SendStatusExtInfo sendStatusExtInfo;
    public int sendType;
    public String senderUid;
    public long senderUin;
    public int sourceType;
    public int subMsgType;
    public long timeStamp;
    public long totalSeq;

    public MsgRecord() {
        this.senderUid = "";
        this.peerUid = "";
        this.channelId = "";
        this.guildId = "";
        this.msgMeta = new byte[0];
        this.sendRemarkName = "";
        this.sendMemberName = "";
        this.sendNickName = "";
        this.guildName = "";
        this.channelName = "";
        this.elements = new ArrayList<>();
        this.auxiliaryElements = new ArrayList<>();
        this.records = new ArrayList<>();
        this.emojiLikesList = new ArrayList<>();
        this.directMsgMembers = new ArrayList<>();
        this.peerName = "";
        this.avatarMeta = "";
        this.avatarPendant = "";
        this.feedId = "";
        this.fromChannelRoleInfo = new FromRoleInfo();
        this.fromGuildRoleInfo = new FromRoleInfo();
        this.levelRoleInfo = new FromRoleInfo();
        this.generalFlags = new byte[0];
        this.msgAttrs = new HashMap<>();
    }

    public AnonymousExtInfo getAnonymousExtInfo() {
        return this.anonymousExtInfo;
    }

    public int getAtType() {
        return this.atType;
    }

    public ArrayList<MsgElement> getAuxiliaryElements() {
        return this.auxiliaryElements;
    }

    public int getAvatarFlag() {
        return this.avatarFlag;
    }

    public String getAvatarMeta() {
        return this.avatarMeta;
    }

    public String getAvatarPendant() {
        return this.avatarPendant;
    }

    public int getCategoryManage() {
        return this.categoryManage;
    }

    public String getChannelId() {
        return this.channelId;
    }

    public String getChannelName() {
        return this.channelName;
    }

    public int getChatType() {
        return this.chatType;
    }

    public GuildClientIdentity getClientIdentityInfo() {
        return this.clientIdentityInfo;
    }

    public long getClientSeq() {
        return this.clientSeq;
    }

    public long getCntSeq() {
        return this.cntSeq;
    }

    public long getCommentCnt() {
        return this.commentCnt;
    }

    public int getDirectMsgFlag() {
        return this.directMsgFlag;
    }

    public ArrayList<DirectMsgMember> getDirectMsgMembers() {
        return this.directMsgMembers;
    }

    public boolean getEditable() {
        return this.editable;
    }

    public ArrayList<MsgElement> getElements() {
        return this.elements;
    }

    public ArrayList<MsgEmojiLikes> getEmojiLikesList() {
        return this.emojiLikesList;
    }

    public byte[] getExtInfoForUI() {
        return this.extInfoForUI;
    }

    public String getFeedId() {
        return this.feedId;
    }

    public Integer getFileGroupSize() {
        return this.fileGroupSize;
    }

    public FoldingInfo getFoldingInfo() {
        return this.foldingInfo;
    }

    public FreqLimitInfo getFreqLimitInfo() {
        return this.freqLimitInfo;
    }

    public long getFromAppid() {
        return this.fromAppid;
    }

    public FromRoleInfo getFromChannelRoleInfo() {
        return this.fromChannelRoleInfo;
    }

    public FromRoleInfo getFromGuildRoleInfo() {
        return this.fromGuildRoleInfo;
    }

    public long getFromUid() {
        return this.fromUid;
    }

    public byte[] getGeneralFlags() {
        return this.generalFlags;
    }

    public long getGuildCode() {
        return this.guildCode;
    }

    public String getGuildId() {
        return this.guildId;
    }

    public String getGuildName() {
        return this.guildName;
    }

    public boolean getIsImportMsg() {
        return this.isImportMsg;
    }

    public boolean getIsOnlineMsg() {
        return this.isOnlineMsg;
    }

    public boolean getIsSupportRoamMsg() {
        return this.isSupportRoamMsg;
    }

    public FromRoleInfo getLevelRoleInfo() {
        return this.levelRoleInfo;
    }

    public HashMap<Integer, MsgAttributeInfo> getMsgAttrs() {
        return this.msgAttrs;
    }

    public byte[] getMsgEventInfo() {
        return this.msgEventInfo;
    }

    public long getMsgId() {
        return this.msgId;
    }

    public byte[] getMsgMeta() {
        return this.msgMeta;
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

    public int getMsgType() {
        return this.msgType;
    }

    public MultiTransInfo getMultiTransInfo() {
        return this.multiTransInfo;
    }

    public int getNameType() {
        return this.nameType;
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

    public GuildMedal getPersonalMedal() {
        return this.personalMedal;
    }

    public long getRecallTime() {
        return this.recallTime;
    }

    public ArrayList<MsgRecord> getRecords() {
        return this.records;
    }

    public long getRoleId() {
        return this.roleId;
    }

    public int getRoleType() {
        return this.roleType;
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

    public SendStatusExtInfo getSendStatusExtInfo() {
        return this.sendStatusExtInfo;
    }

    public int getSendType() {
        return this.sendType;
    }

    public String getSenderUid() {
        return this.senderUid;
    }

    public long getSenderUin() {
        return this.senderUin;
    }

    public int getSourceType() {
        return this.sourceType;
    }

    public int getSubMsgType() {
        return this.subMsgType;
    }

    public long getTimeStamp() {
        return this.timeStamp;
    }

    public long getTotalSeq() {
        return this.totalSeq;
    }

    public MsgRecord(long j, long j2, long j3, long j4, int i, int i2, int i3, int i4, String str, String str2, String str3, String str4, long j5, long j6, long j7, long j8, byte[] bArr, int i5, String str5, String str6, String str7, String str8, String str9, ArrayList<MsgElement> arrayList, ArrayList<MsgRecord> arrayList2, ArrayList<MsgEmojiLikes> arrayList3, long j9, int i6, ArrayList<DirectMsgMember> arrayList4, String str10, FreqLimitInfo freqLimitInfo, boolean z, String str11, String str12, String str13, long j10, long j11, GuildClientIdentity guildClientIdentity, boolean z2, int i7, int i8, FromRoleInfo fromRoleInfo, FromRoleInfo fromRoleInfo2, FromRoleInfo fromRoleInfo3, long j12, boolean z3, byte[] bArr2, long j13, Integer num, FoldingInfo foldingInfo, MultiTransInfo multiTransInfo, long j14, long j15, HashMap<Integer, MsgAttributeInfo> hashMap, AnonymousExtInfo anonymousExtInfo, int i9, int i10, byte[] bArr3, GuildMedal guildMedal, int i11, byte[] bArr4) {
        this.senderUid = "";
        this.peerUid = "";
        this.channelId = "";
        this.guildId = "";
        this.msgMeta = new byte[0];
        this.sendRemarkName = "";
        this.sendMemberName = "";
        this.sendNickName = "";
        this.guildName = "";
        this.channelName = "";
        this.elements = new ArrayList<>();
        this.auxiliaryElements = new ArrayList<>();
        this.records = new ArrayList<>();
        this.emojiLikesList = new ArrayList<>();
        this.directMsgMembers = new ArrayList<>();
        this.peerName = "";
        this.avatarMeta = "";
        this.avatarPendant = "";
        this.feedId = "";
        this.fromChannelRoleInfo = new FromRoleInfo();
        this.fromGuildRoleInfo = new FromRoleInfo();
        this.levelRoleInfo = new FromRoleInfo();
        this.generalFlags = new byte[0];
        this.msgId = j;
        this.msgRandom = j2;
        this.msgSeq = j3;
        this.cntSeq = j4;
        this.chatType = i;
        this.msgType = i2;
        this.subMsgType = i3;
        this.sendType = i4;
        this.senderUid = str;
        this.peerUid = str2;
        this.channelId = str3;
        this.guildId = str4;
        this.guildCode = j5;
        this.fromUid = j6;
        this.fromAppid = j7;
        this.msgTime = j8;
        this.msgMeta = bArr;
        this.sendStatus = i5;
        this.sendRemarkName = str5;
        this.sendMemberName = str6;
        this.sendNickName = str7;
        this.guildName = str8;
        this.channelName = str9;
        this.elements = arrayList;
        this.records = arrayList2;
        this.emojiLikesList = arrayList3;
        this.commentCnt = j9;
        this.directMsgFlag = i6;
        this.directMsgMembers = arrayList4;
        this.peerName = str10;
        this.freqLimitInfo = freqLimitInfo;
        this.editable = z;
        this.avatarMeta = str11;
        this.avatarPendant = str12;
        this.feedId = str13;
        this.roleId = j10;
        this.timeStamp = j11;
        this.clientIdentityInfo = guildClientIdentity;
        this.isImportMsg = z2;
        this.atType = i7;
        this.roleType = i8;
        this.fromChannelRoleInfo = fromRoleInfo;
        this.fromGuildRoleInfo = fromRoleInfo2;
        this.levelRoleInfo = fromRoleInfo3;
        this.recallTime = j12;
        this.isOnlineMsg = z3;
        this.generalFlags = bArr2;
        this.clientSeq = j13;
        this.fileGroupSize = num;
        this.foldingInfo = foldingInfo;
        this.multiTransInfo = multiTransInfo;
        this.senderUin = j14;
        this.peerUin = j15;
        this.msgAttrs = hashMap;
        this.anonymousExtInfo = anonymousExtInfo;
        this.nameType = i9;
        this.avatarFlag = i10;
        this.extInfoForUI = bArr3;
        this.personalMedal = guildMedal;
        this.categoryManage = i11;
        this.msgEventInfo = bArr4;
    }
}
