package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;
import java.util.HashMap;

public interface IKernelRecentContactListener {
    void onDeletedContactsNotify(ArrayList<RecentContactInfo> contacts);
    void onMsgUnreadCountUpdate(HashMap<String, Integer> counts);
    void onRecentContactListChanged(ArrayList<Long> sortedContacts, ArrayList<RecentContactInfo> changedContacts, RecentContactExtra extra);
    void onRecentContactListChangedVer2(ArrayList<RecentContactListChangedInfo> infos, int reason);
    void onRecentContactNotification(ArrayList<RecentContactInfo> contacts, NotificationCommonInfo info, int reason);
}
