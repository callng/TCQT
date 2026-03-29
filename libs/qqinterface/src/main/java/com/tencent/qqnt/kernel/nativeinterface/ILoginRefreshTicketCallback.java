package com.tencent.qqnt.kernel.nativeinterface;

public interface ILoginRefreshTicketCallback {
    void onResult(int i, String str, RefreshTicketResult refreshTicketResult);
}
