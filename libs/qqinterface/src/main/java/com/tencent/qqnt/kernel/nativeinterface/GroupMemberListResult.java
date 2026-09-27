package com.tencent.qqnt.kernel.nativeinterface;

import java.util.HashMap;

/**
 * 群成员列表结果（宿主 `GroupService.getAllMemberList` 的回调数据）。
 *
 * 字段在 QQ 9.3.70 上未混淆：`infos` 为 `uid → 成员信息`。
 */
public class GroupMemberListResult {

    /** 成员：UID → 成员信息。 */
    public HashMap<String, MemberInfo> infos;

    /** 成员 id 列表。 */
    public java.util.ArrayList<String> ids;

    /** 是否已取完。 */
    public boolean finish;

    /** 是否含机器人。 */
    public boolean hasRobot;
}
