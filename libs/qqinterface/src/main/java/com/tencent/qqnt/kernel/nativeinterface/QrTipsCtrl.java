package com.tencent.qqnt.kernel.nativeinterface;

public final class QrTipsCtrl {
    public boolean needSecCheck;
    public int secCheckConfirmTime;
    public QrTipsColor color = QrTipsColor.values()[0];
    public String tips = "";
    public String secCheckTips = "";
    public QrTipsColor secCheckTipsColor = QrTipsColor.values()[0];

    public QrTipsColor getColor() {
        return this.color;
    }

    public boolean getNeedSecCheck() {
        return this.needSecCheck;
    }

    public int getSecCheckConfirmTime() {
        return this.secCheckConfirmTime;
    }

    public String getSecCheckTips() {
        return this.secCheckTips;
    }

    public QrTipsColor getSecCheckTipsColor() {
        return this.secCheckTipsColor;
    }

    public String getTips() {
        return this.tips;
    }
}
