package com.tencent.qqnt.troopmemberlist;

import androidx.lifecycle.LifecycleOwner;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public interface ITroopMemberExtInfoRepoApi extends QRouteApi {
    void fetchTroopAdmin(@NotNull String str, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super List<String>, Unit> function2);
    void fetchTroopOwner(@NotNull String str, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super List<String>, Unit> function2);
}
