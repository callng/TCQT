package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class SysEmojiGroup {
    public String groupName = "";
    public ArrayList<SysEmoji> SysEmojiList = new ArrayList<>();
    public ExtButtonInfo extButton = new ExtButtonInfo();

    public ExtButtonInfo getExtButton() {
        return this.extButton;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public ArrayList<SysEmoji> getSysEmojiList() {
        return this.SysEmojiList;
    }
}
