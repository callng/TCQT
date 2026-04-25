package com.tencent.qqnt.kernel.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import com.tencent.qqnt.kernel.nativeinterface.IKernelLoginService;
import com.tencent.qqnt.kernel.nativeinterface.ILoginTicketCallback;
import org.jetbrains.annotations.Nullable;

public interface ILoginService extends QRouteApi, IKernelLoginService {
    void checkA1TicketExist(long j, long j2, @Nullable ILoginTicketCallback iLoginTicketCallback);
}
