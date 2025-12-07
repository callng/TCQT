package com.tencent.qqnt.kernel.nativeinterface;

public final class Iframe {
    public byte[] iframeSig = new byte[0];
    public byte[] iframeRandstr = new byte[0];
    public byte[] iframeSid = new byte[0];

    public byte[] getIframeRandstr() {
        return this.iframeRandstr;
    }

    public byte[] getIframeSid() {
        return this.iframeSid;
    }

    public byte[] getIframeSig() {
        return this.iframeSig;
    }
}
