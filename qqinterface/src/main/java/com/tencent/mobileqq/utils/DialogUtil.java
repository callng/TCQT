package com.tencent.mobileqq.utils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.List;

public class DialogUtil {
    public static final int ALERT_DIALOG = 230;
    public static final int BUTTON0_ID = 0;
    public static final int BUTTON1_ID = 1;
    public static final int BUTTON2_ID = 2;
    public static final int FAIL_DIALOG = 233;
    public static final int REPORT_LOADING = 231;
    public static final int SUCCESS_DIALOG = 232;

    public DialogUtil() {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCenterTextDialog(Context context, int i, String str, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createContentOnlyDialog(Context context, String str, int i, int i2, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCountdownDialog(Context context, String str, CharSequence charSequence, String str2, String str3, boolean z, int i, int i2, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomCloseDialog(Context context, String str, String str2, String str3, DialogInterface.OnClickListener onClickListener, String str4, DialogInterface.OnClickListener onClickListener2, View.OnClickListener onClickListener3) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrl(Context context, int i) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, int i2, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomProgressDialog(Context context, String str, String str2, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialogThreeBtns createCustomThreeBtnsDialog(Context context, int i) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createDialogInGroupTeamWork(Context context, int i, String str, String str2, String str3, String str4, String str5, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnClickListener onClickListener3) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createDialogWithBanner(Context context, int i, int i2, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createDialogWithCheckBox(Context context, int i, String str, String str2, String str3, boolean z, String str4, String str5, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnClickListener onClickListener3) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createDialogWithIcon(Context context, Bitmap bitmap, String str, String str2, String str3, String str4, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createDialogWithIcon2(Context context, Drawable drawable, String str, String str2, String str3, String str4, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createDisclaimerDialog(Activity activity, String str, String str2, String str3, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createDownloadDialog(Context context, Drawable drawable, String str, String str2, String str3, String str4, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createFileInfoDialog(Context context, Drawable drawable, String str, String str2, String str3, String str4, String str5, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createForceDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createGPSEnableDialog(Activity activity, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createNoTitleDialog(Activity activity, String str) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createPrivacyPolicyDialog(Context context, String str, String str2, int i, int i2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createTipDialog(Context context, int i, String str, int i2, int i3, View.OnClickListener onClickListener, View.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createVerticalThreeBtnDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createWaitingDialog(Activity activity, int i) {
        throw new RuntimeException("Stub!");
    }

    public static String getPermissionWording(String... strArr) {
        throw new RuntimeException("Stub!");
    }

    static int[] integerListToArray(List<Integer> list) {
        throw new RuntimeException("Stub!");
    }

    public static void lambda$createCustomViewDialog$0(DialogInterface.OnClickListener onClickListener, Dialog dialog, View view) {
        throw new RuntimeException("Stub!");
    }

    public static void lambda$createCustomViewDialog$1(DialogInterface.OnClickListener onClickListener, Dialog dialog, View view) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog showLoadingDialog(Context context, String str, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionDialogForOnlineStatusLocation(Activity activity, int i, int i2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionDialogForShareLocation(Activity activity) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionDialogForStorage(Context context) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionSettingDialog(Activity activity, String str, String str2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionSettingDialog2(Activity activity, List<String> list, List<Integer> list2) {
        throw new RuntimeException("Stub!");
    }

    public static ProgressDialog showProgressDialog(Context context, int i) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showSlideAcceptDialog(Activity activity, String str, String str2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnCancelListener onCancelListener) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showWriteSetting(Activity activity, String str, String str2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, String str2, int i2, int i3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrl(Context context, int i, String str, String str2, int i2, int i3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialogThreeBtns createCustomThreeBtnsDialog(Context context, int i, String str, String str2, String str3, String str4, String str5, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnClickListener onClickListener3) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createNoTitleDialog(Activity activity, int i) {
        throw new RuntimeException("Stub!");
    }

    public static Dialog createTipDialog(Context context, int i, String str, int i2, int i3, View.OnClickListener onClickListener, View.OnClickListener onClickListener2, boolean z, boolean z2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createVerticalThreeBtnDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnClickListener onClickListener3) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createDialogWithBanner(Context context, int i, String str, String str2, CharSequence charSequence, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createNoTitleDialog(Activity activity, String str, int i, int i2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionSettingDialog(Activity activity, String str, String str2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnCancelListener onCancelListener) {
        throw new RuntimeException("Stub!");
    }

    public static ProgressDialog showProgressDialog(Context context, Drawable drawable, int i) {
        throw new RuntimeException("Stub!");
    }

    public static ProgressDialog showProgressDialog(Context context, Drawable drawable, int i, int i2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionSettingDialog2(Activity activity, String[] strArr, int[] iArr) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, String str, CharSequence charSequence, int i2, int i3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog showPermissionDialogForStorage(Context context, String str) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, int i2, String str, CharSequence charSequence, int i3, int i4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, int i2, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, int i2, String str, CharSequence charSequence, int i3, int i4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnDismissListener onDismissListener) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialogUrlWithoutAutoLink(Context context, int i, String str, CharSequence charSequence, int i2, int i3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, String str, CharSequence charSequence, String str2, String str3, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, String str2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, String str, String str2, String str3, int i, int i2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, String str, String str2, String str3, String str4, int i2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, int i2, String str, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static QQCustomDialog createCustomDialog(Context context, int i, int i2, String str, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, boolean z) {
        throw new RuntimeException("Stub!");
    }
}
