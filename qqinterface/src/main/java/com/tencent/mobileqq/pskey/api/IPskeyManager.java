package com.tencent.mobileqq.pskey.api;

import kotlin.Deprecated;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import mqq.app.api.IRuntimeService;
import mqq.manager.TicketManager;
import oo1.a;
import oo1.b;

public interface IPskeyManager extends IRuntimeService, TicketManager.IPskeyManager {
    void attackUpdatePskey();

    void clearLocalPskey(@Nullable String[] strArr);

    void getPskey(@NotNull String[] strArr, @NotNull a aVar);

    @Deprecated(message = "仅供内部调用使用，业务请使用getPskey")
    void getPskeyFromRequest(@NotNull String[] strArr, int i, @NotNull a aVar);

    void getUskey(@NotNull String[] strArr, @NotNull a aVar);

    void onAppDestory();

    void onNTKernelSessionCreated();

    void registerPskeyListener(@Nullable b bVar);

    void replacePskey(@Nullable String[] strArr, @NotNull a aVar);

    void replaceUskey(@Nullable String[] strArr, @NotNull a aVar);

    void unregisterPskeyListener(@Nullable b bVar);

    void updateAllPskeyAndUskey();
}
