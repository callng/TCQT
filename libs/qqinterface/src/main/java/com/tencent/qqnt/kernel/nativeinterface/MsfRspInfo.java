package com.tencent.qqnt.kernel.nativeinterface;

import java.util.HashMap;

public final class MsfRspInfo {
    public String errorMsg;
    public byte[] pbBuffer;
    public int ssoRetCode;
    public HashMap<String, byte[]> transInfoMap;
    public int trpcFuncCode;
    public int trpcRetCode;

    public MsfRspInfo() {
        this.errorMsg = "";
        this.pbBuffer = new byte[0];
        this.transInfoMap = new HashMap<>();
    }

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public byte[] getPbBuffer() {
        return this.pbBuffer;
    }

    public int getSsoRetCode() {
        return this.ssoRetCode;
    }

    public HashMap<String, byte[]> getTransInfoMap() {
        return this.transInfoMap;
    }

    public int getTrpcFuncCode() {
        return this.trpcFuncCode;
    }

    public int getTrpcRetCode() {
        return this.trpcRetCode;
    }

    public MsfRspInfo(int i, int i2, int i3, String str, byte[] bArr, HashMap<String, byte[]> hashMap) {
        this.ssoRetCode = i;
        this.trpcRetCode = i2;
        this.trpcFuncCode = i3;
        this.errorMsg = str;
        this.pbBuffer = bArr;
        this.transInfoMap = hashMap;
    }
}
