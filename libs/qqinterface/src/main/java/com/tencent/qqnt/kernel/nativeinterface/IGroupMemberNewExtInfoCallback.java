package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public interface IGroupMemberNewExtInfoCallback {
    void onResult(int code, String message, ArrayList<GroupMemberNewExtInfo> infos);
}
