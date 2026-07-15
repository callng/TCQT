package com.tencent.qqnt.kernel.nativeinterface;

public final class ExtButtonInfo {
    public boolean enableButton;
    public int maxLineLength;
    public String iconUrl = "";
    public String iconScheme = "";

    public boolean getEnableButton() {
        return this.enableButton;
    }

    public String getIconScheme() {
        return this.iconScheme;
    }

    public String getIconUrl() {
        return this.iconUrl;
    }

    public int getMaxLineLength() {
        return this.maxLineLength;
    }
}
