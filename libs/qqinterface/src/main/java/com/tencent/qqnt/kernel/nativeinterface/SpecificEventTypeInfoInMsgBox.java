package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class SpecificEventTypeInfoInMsgBox {
    public int eventTypeInMsgBox;
    public ArrayList<MsgBoxNecessaryMsgInfo> msgInfos;

    public SpecificEventTypeInfoInMsgBox() {
        this.msgInfos = new ArrayList<>();
    }

    public int getEventTypeInMsgBox() {
        return this.eventTypeInMsgBox;
    }

    public ArrayList<MsgBoxNecessaryMsgInfo> getMsgInfos() {
        return this.msgInfos;
    }

    public SpecificEventTypeInfoInMsgBox(int i, ArrayList<MsgBoxNecessaryMsgInfo> arrayList) {
        this.eventTypeInMsgBox = i;
        this.msgInfos = arrayList;
    }
}
