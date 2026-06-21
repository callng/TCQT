package com.tencent.qqnt.troop;

import androidx.lifecycle.LifecycleOwner;

import com.tencent.mobileqq.data.troop.TroopInfo;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public interface ITroopInfoRepoApi extends QRouteApi {
    void batchFetchTroopBasicInfo(@NotNull ArrayList<Long> arrayList);
    void fetchTroopBasicInfo(@Nullable String str, @NotNull String str2, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super TroopInfo, Unit> function2);
    void fetchTroopBasicInfoWithExt(@Nullable String str, @NotNull String str2, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super TroopInfo, Unit> function2);
    @Nullable
    TroopInfo getTroopInfoFromCache(@Nullable String str);
}
