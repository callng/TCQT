package com.tencent.qqnt.ntrelation.friendsinfo.api;

import com.tencent.qqnt.ntrelation.friendsinfo.bean.d;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Map;

public interface IFriendsInfoService extends QRouteApi {
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
