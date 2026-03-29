package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import java.util.HashMap;

public class MSFPkgCompressConfig implements MSFConfig {
    int mCompressThreshold;
    HashMap<String, Integer> mCompressThresholdMap;
    boolean mIsOpen;
    int mType;

    public MSFPkgCompressConfig() {
        this.mCompressThresholdMap = new HashMap<>();
    }

    public int getCompressThreshold() {
        return this.mCompressThreshold;
    }

    public void setCompressThreshold(int i) {
        this.mCompressThreshold = i;
    }

    @NonNull
    public HashMap<String, Integer> getCompressThresholdMap() {
        return this.mCompressThresholdMap;
    }

    public void setCompressThresholdMap(@NonNull HashMap<String, Integer> hashMap) {
        this.mCompressThresholdMap = hashMap;
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
        return "MSFPkgCompressConfig{mType=" + this.mType +
                ",mIsOpen=" + this.mIsOpen +
                ",mCompressThreshold=" + this.mCompressThreshold +
                ",mCompressThresholdMap=" + this.mCompressThresholdMap + ",}";
    }

    public MSFPkgCompressConfig(int i, boolean z, int i2, @NonNull HashMap<String, Integer> hashMap) {
        this.mType = i;
        this.mIsOpen = z;
        this.mCompressThreshold = i2;
        this.mCompressThresholdMap = hashMap;
    }
}
