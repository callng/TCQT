package com.tencent.qqnt.kernel.nativeinterface;

public final class LoginResult {
    public Account account = new Account();
    public LoginTicketInfo ticket = new LoginTicketInfo();
    public LoginRspInfo rspInfo = new LoginRspInfo();

    public Account getAccount() {
        return this.account;
    }

    public LoginRspInfo getRspInfo() {
        return this.rspInfo;
    }

    public LoginTicketInfo getTicket() {
        return this.ticket;
    }
}
