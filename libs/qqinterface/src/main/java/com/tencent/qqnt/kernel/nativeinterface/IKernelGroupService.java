package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole;
import java.util.ArrayList;

public interface IKernelGroupService {
    void setGroupShutUp(long groupId, boolean isShutUp, IOperateCallback iOperateCallback);
    void setMemberShutUp(long groupId, ArrayList<GroupMemberShutUpInfo> info, IOperateCallback iOperateCallback);
    void modifyMemberRole(long groupId, String uid, MemberRole memberRole, IOperateCallback iOperateCallback);
    void kickMember(long groupId, ArrayList<String> kickUidList, boolean isBlock, String str, IKickMemberOperateCallback iKickMemberOperateCallback);
    void kickMemberV2(KickMemberReq kickMemberReq, IKickMemberCallback iKickMemberCallback);
    void modifyMemberCardName(long groupId, String uid, String newName, IOperateCallback iOperateCallback);
    void getUidByUins(ArrayList<Long> uinList, IGroupMemberUidCallback iGroupMemberUidCallback);
    void getUinByUids(ArrayList<String> uidList, IGroupMemberUinCallback iGroupMemberUinCallback);
}
