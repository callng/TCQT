package com.tencent.qqnt.kernel.nativeinterface;

public final class FoldingInfo {
    public BeatTypeEnum beatType;
    public String greyPrompt;
    public String toast;

    public FoldingInfo() {
        this.beatType = BeatTypeEnum.values()[0];
        this.greyPrompt = "";
        this.toast = "";
    }

    public BeatTypeEnum getBeatType() {
        return this.beatType;
    }

    public String getGreyPrompt() {
        return this.greyPrompt;
    }

    public String getToast() {
        return this.toast;
    }

    public FoldingInfo(BeatTypeEnum beatTypeEnum, String str, String str2) {
        BeatTypeEnum beatTypeEnum2 = BeatTypeEnum.values()[0];
        this.beatType = beatTypeEnum;
        this.greyPrompt = str;
        this.toast = str2;
    }
}
