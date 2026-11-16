package com.tencent.qqnt.kernel.nativeinterface;

public class RecentContactFetchParam {
    public int listType;
    public boolean fetchOld;
    public int count;
    public AnchorPointContactInfo anchorPointContact;

    public RecentContactFetchParam(int listType, boolean fetchOld, int count, AnchorPointContactInfo anchorPointContact) {
        this.listType = listType;
        this.fetchOld = fetchOld;
        this.count = count;
        this.anchorPointContact = anchorPointContact;
    }
}
