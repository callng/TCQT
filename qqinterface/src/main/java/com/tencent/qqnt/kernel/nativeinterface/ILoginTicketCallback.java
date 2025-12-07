package com.tencent.qqnt.kernel.nativeinterface;

public interface ILoginTicketCallback {
    void onResult(int i, String str, LoginTicketInfo loginTicketInfo);
}
