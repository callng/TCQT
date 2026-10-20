package com.tencent.qqnt.kernel.nativeinterface;

public interface IGroupMemberListCallback {
    void onResult(int code, String message, GroupMemberListResult result);
}
