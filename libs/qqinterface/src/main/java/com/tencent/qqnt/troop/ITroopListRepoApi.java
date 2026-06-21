package com.tencent.qqnt.troop;

import com.tencent.mobileqq.data.troop.TroopInfo;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ITroopListRepoApi extends QRouteApi {
    void deleteTroopInCache(@NotNull String str);
    @Nullable
    TroopInfo getTroopInfoFromCache(@Nullable String str);
}
