package com.tencent.qqnt.kernel.nativeinterface;

/**
 * 群成员列表回调（宿主 `GroupService.getAllMemberList` 的参数）。
 *
 * 签名与 QQ 9.3.70 一致：`onResult(int 错误码, String 错误信息, GroupMemberListResult 结果)`。
 */
public interface IGroupMemberListCallback {
    void onResult(int i, String str, GroupMemberListResult groupMemberListResult);
}
