package com.tencent.qqnt.kernel.nativeinterface;

public final class RapidLoginReq {
    public long destAppid;
    public long destSubAppid;
    public long uin;
    public String scheme = "";
    public String bundleId = "";
    public String publicKey = "";

    public String getBundleId() {
        return this.bundleId;
    }

    public long getDestAppid() {
        return this.destAppid;
    }

    public long getDestSubAppid() {
        return this.destSubAppid;
    }

    public String getPublicKey() {
        return this.publicKey;
    }

    public String getScheme() {
        return this.scheme;
    }

    public long getUin() {
        return this.uin;
    }
}
