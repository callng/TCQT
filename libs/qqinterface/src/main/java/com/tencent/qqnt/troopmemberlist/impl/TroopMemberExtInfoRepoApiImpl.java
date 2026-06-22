package com.tencent.qqnt.troopmemberlist.impl;

import androidx.lifecycle.LifecycleOwner;
import com.tencent.qqnt.troopmemberlist.ITroopMemberExtInfoRepoApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public final class TroopMemberExtInfoRepoApiImpl implements ITroopMemberExtInfoRepoApi {

    public void fetchTroopAdmin(@NotNull String str, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super List<String>, Unit> function2) {
        throw new RuntimeException("Stub!");
    }

    public void fetchTroopOwner(@NotNull String str, @Nullable LifecycleOwner lifecycleOwner, @Nullable Function2<? super Boolean, ? super List<String>, Unit> function2) {
        throw new RuntimeException("Stub!");
    }
}
