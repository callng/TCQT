package com.tencent.qqnt.ntrelation.friendsinfo.api;

import com.tencent.qqnt.ntrelation.friendsinfo.bean.d;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IFriendsInfoService extends QRouteApi {

    /**
     * 全部好友。
     *
     * @param str 分组 id；传空串表示不筛选
     */
    @Nullable
    List<d> getAllFriend(@Nullable String str);

    /** 备注（按 UID）。 */
    @Nullable
    String getRemarkWithUid(@NotNull String str, @Nullable String str2);

    /** 昵称（按 UID）。 */
    @Nullable
    String getNickWithUid(@NotNull String str, @Nullable String str2);

    @Nullable
    d getFriendsSimpleInfoWithUid(@NotNull String str, @Nullable String str2);

    int getFriendCount(@Nullable String str);

    @Nullable
    String getUidFromUin(@NotNull String str);

    @Nullable
    String getUinFromUid(@NotNull String str);

    boolean isFriend(@NotNull String str, @Nullable String str2);

    @NotNull
    Map<String, Boolean> isFriends(@NotNull String str, @NotNull ArrayList<String> arrayList);

    boolean isValidUid(@NotNull String str, @Nullable String str2);

    boolean isValidUin(@NotNull String str, @Nullable String str2);
}
