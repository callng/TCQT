package com.tencent.robot.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public interface IRobotIdentityApi extends QRouteApi {
    void checkAndAddFriend(@NotNull String str, @NotNull Function0<Unit> function0);

    boolean isMatchRobotChatType(@Nullable Integer num);

    boolean isMatchRobotUinType(@Nullable Integer num);

    boolean isRobotUin(@Nullable Long l);

    boolean isRobotUin(@Nullable String str);
}
