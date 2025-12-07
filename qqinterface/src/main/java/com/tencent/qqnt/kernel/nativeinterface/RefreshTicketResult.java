package com.tencent.qqnt.kernel.nativeinterface;

public final class RefreshTicketResult {
    public LoginTicketInfo ticket = new LoginTicketInfo();
    public SvrErrorInfo errorInfo = new SvrErrorInfo();
    public Account account = new Account();

    public Account getAccount() {
        return this.account;
    }

    public SvrErrorInfo getErrorInfo() {
        return this.errorInfo;
    }

    public LoginTicketInfo getTicket() {
        return this.ticket;
    }
}
