package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.Contact;

public final class UnreadCntInfo {
    public UnreadCnt allUnreadCnt;
    public UnreadCnt atallUnreadCnt;
    public UnreadCnt atmeUnreadCnt;
    public byte[] headerUrl;
    public GuildGroupOptType lastRelatedToFeedType;
    public int lastRelatedToMeType;
    public Contact peer;
    public int relatedToMeCnt;
    public String relatedToMeString;
    public long relatedToMeStringTime;
    public UnreadCnt showUnreadCnt;

    public UnreadCntInfo() {
        this.showUnreadCnt = new UnreadCnt();
        this.allUnreadCnt = new UnreadCnt();
        this.atmeUnreadCnt = new UnreadCnt();
        this.atallUnreadCnt = new UnreadCnt();
        this.peer = new Contact();
        this.relatedToMeString = "";
        this.lastRelatedToFeedType = GuildGroupOptType.values()[0];
        this.headerUrl = new byte[0];
    }

    public UnreadCnt getAllUnreadCnt() {
        return this.allUnreadCnt;
    }

    public UnreadCnt getAtallUnreadCnt() {
        return this.atallUnreadCnt;
    }

    public UnreadCnt getAtmeUnreadCnt() {
        return this.atmeUnreadCnt;
    }

    public byte[] getHeaderUrl() {
        return this.headerUrl;
    }

    public GuildGroupOptType getLastRelatedToFeedType() {
        return this.lastRelatedToFeedType;
    }

    public int getLastRelatedToMeType() {
        return this.lastRelatedToMeType;
    }

    public Contact getPeer() {
        return this.peer;
    }

    public int getRelatedToMeCnt() {
        return this.relatedToMeCnt;
    }

    public String getRelatedToMeString() {
        return this.relatedToMeString;
    }

    public long getRelatedToMeStringTime() {
        return this.relatedToMeStringTime;
    }

    public UnreadCnt getShowUnreadCnt() {
        return this.showUnreadCnt;
    }

    public UnreadCntInfo(UnreadCnt unreadCnt, UnreadCnt unreadCnt2, UnreadCnt unreadCnt3, UnreadCnt unreadCnt4, Contact contact, String str, int i, int i2, long j, GuildGroupOptType guildGroupOptType, byte[] bArr) {
        this.showUnreadCnt = unreadCnt;
        this.allUnreadCnt = unreadCnt2;
        this.atmeUnreadCnt = unreadCnt3;
        this.atallUnreadCnt = unreadCnt4;
        this.peer = contact;
        this.relatedToMeString = str;
        this.relatedToMeCnt = i;
        this.lastRelatedToMeType = i2;
        this.relatedToMeStringTime = j;
        this.lastRelatedToFeedType = guildGroupOptType;
        this.headerUrl = bArr;
    }
}
