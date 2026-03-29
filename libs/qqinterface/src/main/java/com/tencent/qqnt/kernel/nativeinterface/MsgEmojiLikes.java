package com.tencent.qqnt.kernel.nativeinterface;

public final class MsgEmojiLikes {
    public String emojiId;
    public long emojiType;
    public boolean isClicked;
    public long likesCnt;

    public MsgEmojiLikes() {
        this.emojiId = "";
    }

    public String getEmojiId() {
        return this.emojiId;
    }

    public long getEmojiType() {
        return this.emojiType;
    }

    public boolean getIsClicked() {
        return this.isClicked;
    }

    public long getLikesCnt() {
        return this.likesCnt;
    }

    public MsgEmojiLikes(String str, long j, long j2, boolean z) {
        this.emojiId = str;
        this.emojiType = j;
        this.likesCnt = j2;
        this.isClicked = z;
    }
}
