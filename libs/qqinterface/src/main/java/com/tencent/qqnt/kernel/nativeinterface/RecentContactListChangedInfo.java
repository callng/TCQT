package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public class RecentContactListChangedInfo {
    public int listType;
    public ArrayList<RecentContactInfo> changedList = new ArrayList<>();
    public ArrayList<Long> sortedContactList = new ArrayList<>();
    public long firstContactPos;
    public boolean moreDataAtTheBottom;
}
