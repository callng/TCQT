package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class Notice {
    public long createTime;
    public int noticeType;
    public String noticeId = "";
    public NoticePattonInfo noticePatton = new NoticePattonInfo();
    public StFeed feed = new StFeed();
    public ArrayList<Integer> opMask = new ArrayList<>();
    public StUser opUser = new StUser();
    public PattonAction action = new PattonAction();
    public StCommonExt ext = new StCommonExt();
    public StComment feedComment = new StComment();
    public StReply feedReply = new StReply();
    public ArrayList<StRichMsg> noticeMessage = new ArrayList<>();

    public PattonAction getAction() {
        return this.action;
    }

    public long getCreateTime() {
        return this.createTime;
    }

    public StCommonExt getExt() {
        return this.ext;
    }

    public StFeed getFeed() {
        return this.feed;
    }

    public StComment getFeedComment() {
        return this.feedComment;
    }

    public StReply getFeedReply() {
        return this.feedReply;
    }

    public String getNoticeId() {
        return this.noticeId;
    }

    public ArrayList<StRichMsg> getNoticeMessage() {
        return this.noticeMessage;
    }

    public NoticePattonInfo getNoticePatton() {
        return this.noticePatton;
    }

    public int getNoticeType() {
        return this.noticeType;
    }

    public ArrayList<Integer> getOpMask() {
        return this.opMask;
    }

    public StUser getOpUser() {
        return this.opUser;
    }

    public void setAction(PattonAction pattonAction) {
        this.action = pattonAction;
    }

    public void setCreateTime(long j) {
        this.createTime = j;
    }

    public void setExt(StCommonExt stCommonExt) {
        this.ext = stCommonExt;
    }

    public void setFeed(StFeed stFeed) {
        this.feed = stFeed;
    }

    public void setFeedComment(StComment stComment) {
        this.feedComment = stComment;
    }

    public void setFeedReply(StReply stReply) {
        this.feedReply = stReply;
    }

    public void setNoticeId(String str) {
        this.noticeId = str;
    }

    public void setNoticeMessage(ArrayList<StRichMsg> arrayList) {
        this.noticeMessage = arrayList;
    }

    public void setNoticePatton(NoticePattonInfo noticePattonInfo) {
        this.noticePatton = noticePattonInfo;
    }

    public void setNoticeType(int i) {
        this.noticeType = i;
    }

    public void setOpMask(ArrayList<Integer> arrayList) {
        this.opMask = arrayList;
    }

    public void setOpUser(StUser stUser) {
        this.opUser = stUser;
    }
}
