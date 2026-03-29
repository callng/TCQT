package com.tencent.qqnt.kernel.nativeinterface;

public final class ErrorDetail {
    public ErrorNeedVerifyNewDevice msgNeedVerifyNewDevice = new ErrorNeedVerifyNewDevice();
    public ErrorUnregistered msgUnregistered = new ErrorUnregistered();
    public ErrorBeenForbiden msgBeenForbiden = new ErrorBeenForbiden();
    public ErrorNiceAccountExpire msgNiceAccountExpire = new ErrorNiceAccountExpire();
    public ErrorCheckUpSms checkUpSms = new ErrorCheckUpSms();

    public ErrorCheckUpSms getCheckUpSms() {
        return this.checkUpSms;
    }

    public ErrorBeenForbiden getMsgBeenForbiden() {
        return this.msgBeenForbiden;
    }

    public ErrorNeedVerifyNewDevice getMsgNeedVerifyNewDevice() {
        return this.msgNeedVerifyNewDevice;
    }

    public ErrorNiceAccountExpire getMsgNiceAccountExpire() {
        return this.msgNiceAccountExpire;
    }

    public ErrorUnregistered getMsgUnregistered() {
        return this.msgUnregistered;
    }
}
