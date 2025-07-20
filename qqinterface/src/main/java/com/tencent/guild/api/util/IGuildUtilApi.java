package com.tencent.guild.api.util;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import com.tencent.mobileqq.qroute.QRouteApi;
import com.tencent.qqnt.kernel.nativeinterface.NetStatusType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import sn0.b;
import sn0.c;

public interface IGuildUtilApi extends QRouteApi {
    void beaconReport(@Nullable String str, @Nullable String str2, boolean z, @Nullable HashMap<String, String> hashMap, boolean z2);

    @NotNull
    c createGuardManagerDelegate(@NotNull b bVar);

    void fillJubaoIntent(@Nullable Intent intent, @NotNull String str, int i, @NotNull String str2);

    @Nullable
    String getAppVersion();

    @Nullable
    byte[] getGUID();

    @NotNull
    String getNetworkName();

    @NotNull
    NetStatusType getNetworkType();

    boolean isApplicationForeground();

    boolean isColorUser();

    boolean isGuildUser();

    boolean isGuildWalletMsgType(int i);

    boolean isMyPersonalGuildWalletMsgType(int i);

    boolean isNowThemeIsNight();

    boolean isSdkEnable();

    boolean isSimpleUIMode();

    void loadNativeLibrary(@NotNull Context context, @NotNull String str, int i, boolean z);

    boolean needCheckSourceChannelIsValid(@NotNull String str);

    void onConfigurationChanged(@NotNull Configuration configuration);

    void onRegisterCountInstruments(@NotNull ArrayList<String> arrayList, int i, int i2);

    void onRegisterValueInstrumentsWithBoundary(@NotNull ArrayList<String> arrayList, @NotNull ArrayList<Double> arrayList2, int i, int i2);

    void onReportCountIndicators(@Nullable HashMap<String, String> hashMap, @NotNull String str, long j);

    void onReportValueIndicators(@Nullable HashMap<String, String> hashMap, @NotNull String str, double d);

    void onTabChanged(boolean z);

    void onThemeChanged();

    void openNormalUrl(@NotNull Context context, @Nullable String str);

    void showToast(@NotNull Context context, @NotNull CharSequence charSequence, int i);
}
