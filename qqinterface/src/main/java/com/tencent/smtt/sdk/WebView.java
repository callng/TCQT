package com.tencent.smtt.sdk;

import java.util.Map;
import android.webkit.JavascriptInterface;

public abstract class WebView {
    public void loadData(String str, String str2, String str3) {

    }

    public String getUrl() {
        return "";
    }

    public boolean getIsX5Core() {
        return false;
    }

    public void loadDataWithBaseURL(String str, String str2, String str3, String str4, String str5) {

    }

    public void loadUrl(String str) {

    }

    public void loadUrl(String str, Map<String, String> map) {

    }

    public void addJavascriptInterface(Object bridge, String name) {

    }
}
