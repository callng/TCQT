package com.tencent.mobileqq.msfcore;

public class MSFTraceConfig implements MSFConfig {
    boolean mIsOpen;
    int mType;

    public MSFTraceConfig() {
    }

    public boolean getIsOpen() {
        return this.mIsOpen;
    }

    public void setIsOpen(boolean z) {
        this.mIsOpen = z;
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @Override
    public String toString() {
        return "MSFTraceConfig{mType=" + this.mType + ",mIsOpen=" + this.mIsOpen + ",}";
    }

    public MSFTraceConfig(int i, boolean z) {
        this.mType = i;
        this.mIsOpen = z;
    }
}
