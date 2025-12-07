package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class UinInfo implements Serializable {
    public byte[] a1Sig;
    long serialVersionUID = 1;
    public String maskUin = "";
    public String nick = "";
    public String imageUrl = "";
    public byte[] encryptUin = new byte[0];
    public String keyUin = "";

    public byte[] getA1Sig() {
        return this.a1Sig;
    }

    public byte[] getEncryptUin() {
        return this.encryptUin;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public String getKeyUin() {
        return this.keyUin;
    }

    public String getMaskUin() {
        return this.maskUin;
    }

    public String getNick() {
        return this.nick;
    }

    public void setA1Sig(byte[] bArr) {
        this.a1Sig = bArr;
    }

    public void setEncryptUin(byte[] bArr) {
        this.encryptUin = bArr;
    }

    public void setImageUrl(String str) {
        this.imageUrl = str;
    }

    public void setKeyUin(String str) {
        this.keyUin = str;
    }

    public void setMaskUin(String str) {
        this.maskUin = str;
    }

    public void setNick(String str) {
        this.nick = str;
    }
}
