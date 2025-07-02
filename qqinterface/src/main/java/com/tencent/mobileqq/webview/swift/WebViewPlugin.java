package com.tencent.mobileqq.webview.swift;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public abstract class WebViewPlugin {
    public static String toJsScript(String str, JSONObject jSONObject, JSONObject jSONObject2) {
        //return "window.mqq && mqq.execEventCallback && mqq.execEventCallback(" + com.tencent.mobileqq.webview.util.s.j(str) + "," + jSONObject + "," + jSONObject2 + ");";
        return "";
    }

    @NotNull
    public abstract String getNameSpace();

    public boolean handleJsRequest(JsBridgeListener jsBridgeListener, String unknown, String namespace, String function, String... args) {
        return false;
    }

    public void callJs4OpenApiIfNeeded(String function, int resultCode, Object result) {

    }

    public void callJs(String jsFunction, String... args) {
    }
}
