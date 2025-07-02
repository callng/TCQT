package com.tencent.mobileqq.webview.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;

public interface IJsApi extends QRouteApi {
    <T> T getAntiphingHandlerPlugin();

    <T> T getConfessPlugin();

    @NotNull
    String getConfessPluginNameSpace();

    <T> T getDataApiPlugin();

    <T> T getDocxApiPlugin();

    <T> T getEventApiPlugin();

    <T> T getHippyCallBackListenerWebViewPlugin();

    <T> T getMediaApiPlugin();

    <T> T getOfflinePlugin();

    <T> T getOpenCenterPlugin();

    <T> T getPayJsPlugin();

    <T> T getPtloginPlugin();

    <T> T getQQApiPlugin();

    @NotNull
    String getQQApiPluginNameSpace();

    <T> T getQQIliveJsPlugin();

    <T> T getQWalletBluetoothJsPlugin();

    <T> T getQWalletCommonHbJsPlugin();

    <T> T getQWalletCommonJsPlugin();

    <T> T getQWalletMixJsPlugin();

    <T> T getQWalletPayJsPlugin();

    <T> T getReportPlugin();

    <T> T getSSOWebviewPlugin();

    <T> T getSensorAPIJavaScript();

    <T> T getShareApiPlugin();

    <T> T getTogetherBusinessForWebPlugin();

    <T> T getUIApiPlugin();

    <T> T getVasCommonJsPlugin();

    <T> T getVasWebReportPlugin();

    <T> T getWebForceHttpsPlugin();

    <T> T getWebSecurityPluginV2Plugin();

    <T> T getWebSoPlugin();

    <T> T getWebViewJumpPlugin();

    @NotNull
    String getWebViewJumpPluginNameSpace();

    <T> T getWeizhengquanJsPlugin();
}
