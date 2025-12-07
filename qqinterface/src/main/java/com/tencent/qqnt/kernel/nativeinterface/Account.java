package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class Account implements IKernelModel, Serializable {
    public long uin;
    long serialVersionUID = 1;
    public String uid = "";
    public UserProfile userProfile = new UserProfile();

    public String getUid() {
        return this.uid;
    }

    public long getUin() {
        return this.uin;
    }

    public UserProfile getUserProfile() {
        return this.userProfile;
    }

    public void setUid(String str) {
        this.uid = str;
    }

    public void setUin(long j) {
        this.uin = j;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }
}
