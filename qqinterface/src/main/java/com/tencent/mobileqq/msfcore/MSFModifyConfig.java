package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import java.util.HashMap;

public class MSFModifyConfig implements MSFConfig {
    HashMap<String, Boolean> mConfigMap;
    int mType;

    public MSFModifyConfig() {
        this.mConfigMap = new HashMap<>();
    }

    public int getType() {
        return this.mType;
    }

    public void setType(int i) {
        this.mType = i;
    }

    @NonNull
    public HashMap<String, Boolean> getConfigMap() {
        return this.mConfigMap;
    }

    public void setConfigMap(@NonNull HashMap<String, Boolean> hashMap) {
        this.mConfigMap = hashMap;
    }

    @Override
    public String toString() {
        return "MSFModifyConfig{mType=" + this.mType +
                ",mConfigMap=" + this.mConfigMap + ",}";
    }

    public MSFModifyConfig(int i, @NonNull HashMap<String, Boolean> hashMap) {
        this.mType = i;
        this.mConfigMap = hashMap;
    }
}
