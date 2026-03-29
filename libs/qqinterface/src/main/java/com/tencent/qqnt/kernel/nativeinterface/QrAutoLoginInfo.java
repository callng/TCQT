package com.tencent.qqnt.kernel.nativeinterface;

public final class QrAutoLoginInfo {
    public boolean isShowAutoLoginSwitch;
    public QrAutoLoginSwitchState autoLoginSwitchState = QrAutoLoginSwitchState.values()[0];
    public String tipsTitle = "";
    public String tipsContent = "";

    public QrAutoLoginSwitchState getAutoLoginSwitchState() {
        return this.autoLoginSwitchState;
    }

    public boolean getIsShowAutoLoginSwitch() {
        return this.isShowAutoLoginSwitch;
    }

    public String getTipsContent() {
        return this.tipsContent;
    }

    public String getTipsTitle() {
        return this.tipsTitle;
    }
}
