package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;

public class MSFExternNetworkInfo {
    String mNetIdentifier;
    String mNetName;
    int mSubType;

    public MSFExternNetworkInfo() {
        this.mNetName = "";
        this.mNetIdentifier = "";
    }

    @NonNull
    public String getNetIdentifier() {
        return this.mNetIdentifier;
    }

    @NonNull
    public String getNetName() {
        return this.mNetName;
    }

    public int getSubType() {
        return this.mSubType;
    }

    public void setNetIdentifier(@NonNull String str) {
        this.mNetIdentifier = str;
    }

    public void setNetName(@NonNull String str) {
        this.mNetName = str;
    }

    public void setSubType(int i) {
        this.mSubType = i;
    }

    public String toString() {
        return "MSFExternNetworkInfo{mNetName=" + this.mNetName + ",mNetIdentifier=" + this.mNetIdentifier + ",mSubType=" + this.mSubType + ",}";
    }

    public MSFExternNetworkInfo(@NonNull String str, @NonNull String str2, int i) {
        this.mNetName = str;
        this.mNetIdentifier = str2;
        this.mSubType = i;
    }
}
