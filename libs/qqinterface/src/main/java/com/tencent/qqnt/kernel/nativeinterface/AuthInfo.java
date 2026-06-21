package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class AuthInfo implements Serializable {
    public long expireTime;
    public int resId;
    public String subject = "";
    public String role = "";
    public String uniqueCode = "";
    public String source = "";

    public long getExpireTime() {
        return this.expireTime;
    }

    public int getResId() {
        return this.resId;
    }

    public String getRole() {
        return this.role;
    }

    public String getSource() {
        return this.source;
    }

    public String getSubject() {
        return this.subject;
    }

    public String getUniqueCode() {
        return this.uniqueCode;
    }
}
