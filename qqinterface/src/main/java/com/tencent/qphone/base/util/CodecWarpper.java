package com.tencent.qphone.base.util;

public abstract class CodecWarpper {
    public abstract void onResponse(int i2, Object obj, int i3);

    public abstract void onResponse(int i2, Object obj, int i3, byte[] bArr);

    public abstract int onSSOPingResponse(byte[] bArr, int i2);

    public abstract void onInvalidSign();

    public static byte[] nativeEncodeRequest(int i, String str, String str2, String str3, String str4, String str5, byte[] bArr, int i2, int i3, String str6, byte b, byte b2, byte[] bArr2, byte[] bArr3, byte[] bArr4, boolean z) {
        return null;
    }

    public static byte[] nativeEncodeRequest(int i, String str, String str2, String str3, String str4, String str5, byte[] bArr, int i2, int i3, String str6, byte b, byte b2, byte b3, byte[] bArr2, byte[] bArr3, byte[] bArr4, boolean z) {
        return null;
    }

    public static byte[] nativeEncodeRequest(int i, String str, String str2, String str3, String str4, String str5, byte[] bArr, int i2, int i3, String str6, byte b, byte b2, byte[] bArr2, boolean z) {
        return null;
    }
}
