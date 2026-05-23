package com.tencent.mobileqq.sign;

import android.text.TextUtils;
import com.tencent.mobileqq.fe.EventCallback;
import com.tencent.mobileqq.qsec.qsecurity.QSec;

public class QQSecuritySign {

    private static final String TAG = "QQSecuritySDK";
    private static QQSecuritySign sInstance;
    private String mExtra;

    public static class SignResult {
        public byte[] extra;
        public byte[] sign;
        public byte[] token;
    }

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

    private native SignResult getSign(QSec qSec, String str, String str2, byte[] bArr, byte[] bArr2, String str3);

    public native void dispatchEvent(String str, String str2, EventCallback eventCallback);

    public native void dispatchEventPB(String str, String str2, byte[] bArr, EventCallback eventCallback);

    public SignResult getSign(QSec qSec, String str, byte[] bArr, byte[] bArr2, String str2) {
        if (bArr != null && bArr.length > 0) {
            if (TextUtils.isEmpty(str)) {
                return new SignResult();
            }
            if (TextUtils.isEmpty(this.mExtra)) {
                this.mExtra = "";
            }
            return getSign(qSec, this.mExtra, str, bArr, bArr2, str2);
        }
        return new SignResult();
    }

    public void init(String str) {
        this.mExtra = str;
    }

    public native void initSafeMode(boolean z);

    public native void notifyCamera(String str, String str2, String str3, String str4, String str5, String str6, EventCallback eventCallback);

    public native void notifyFaceDetect(String str, String str2, String str3, EventCallback eventCallback);

    public native void ocrAndEmbedingReport(String str, String str2, String str3, String str4, float[] fArr, float f);

    public native void requestToken();

    public native void requestTokenMain(boolean z);

    public native void safeUiReport(String str, String str2, String str3, EventCallback eventCallback);
}
