package com.tencent.mobileqq.sign;

import com.tencent.mobileqq.fe.EventCallback;
import com.tencent.mobileqq.qsec.qsecurity.QSec;

public class QQSecuritySign {

    private static QQSecuritySign sInstance;

    public static synchronized QQSecuritySign getInstance() {
        QQSecuritySign qQSecuritySign;
        synchronized (QQSecuritySign.class) {
            if (sInstance == null) {
                sInstance = new QQSecuritySign();
            }
            qQSecuritySign = sInstance;
        }
        return qQSecuritySign;
    }

    public native void dispatchEvent(String str, String str2, EventCallback eventCallback);

    public native void dispatchEventPB(String str, String str2, byte[] bArr, EventCallback eventCallback);

    public SignResult getSign(QSec qSec, String str, byte[] bArr, byte[] bArr2, String str2) {
        throw new RuntimeException("Stub!");
    }

    public class SignResult {
        public byte[] extra;
        public byte[] sign;
        public byte[] token;
    }
}
