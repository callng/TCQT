package com.tencent.mobileqq.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.tencent.qqlive.module.videoreport.inject.dialog.ReportDialog;

public class QQCustomDialog extends ReportDialog {
    public static final int WHICH_CANCEL = 2;
    public static final int WHICH_NEGATIVE = 0;
    public static final int WHICH_POSITIVE = 1;
    protected LinearLayout bodyLayout;
    CheckBox checkBox;
    TextView checkTxt;
    protected TextView countText;
    protected View dialogDivider;
    protected EditText dialogEdit;
    TextView dummyFirstPreviewImage;
    TextView editPicTag;
    View framePreview;
    public ImageView framePreviewImage;
    LayoutInflater inflater;
    String[] items;
    protected TextView lBtn;
    int mThemeId;
    DialogInterface.OnClickListener onArrayItemClick;
    ImageView operateImage;
    TextView previewImage;
    ProgressBar progressBar;
    protected TextView rBtn;
    ViewGroup rootView;
    Object tag;
    protected TextView text;
    protected TextView title;

    public QQCustomDialog(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }

    private void applyThemeId() {
        throw new RuntimeException("Stub!");
    }

    public void expendCheckLayoutTouchRect(LinearLayout linearLayout) {
        throw new RuntimeException("Stub!");
    }

    private int getScreenWidth() {
        throw new RuntimeException("Stub!");
    }

    private void printDialogContent() {
        throw new RuntimeException("Stub!");
    }

    public static void showPermissionRequestDialog(Activity activity, String str, String str2, String str3, String str4, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2) {
        throw new RuntimeException("Stub!");
    }

    public static void showPermissionSettingDialog(Activity activity, String str) {
        throw new RuntimeException("Stub!");
    }

    private void widthCompat() {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog addCenterView(View view, LinearLayout.LayoutParams layoutParams) {
        throw new RuntimeException("Stub!");
    }

    public void addPreviewView(View view) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog addView(View view) {
        throw new RuntimeException("Stub!");
    }

    public void adjustMessageBottomMargin(float f) {
        throw new RuntimeException("Stub!");
    }

    public void adjustMessageLayout(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void adjustMessageTopBottomMargin(float f, float f2) {
        throw new RuntimeException("Stub!");
    }

    public void adjustTitle() {
        throw new RuntimeException("Stub!");
    }

    protected int customWhichToCallBack(int i) {
        throw new RuntimeException("Stub!");
    }

    public void dismiss() {
        throw new RuntimeException("Stub!");
    }

    public void dismissMessage() {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        throw new RuntimeException("Stub!");
    }

    public TextView getBtnLeft() {
        throw new RuntimeException("Stub!");
    }

    public TextView getBtnight() {
        throw new RuntimeException("Stub!");
    }

    public boolean getCheckBoxState() {
        throw new RuntimeException("Stub!");
    }

    public View getDialogDivider() {
        throw new RuntimeException("Stub!");
    }

    protected int getDialogListItemLayout() {
        throw new RuntimeException("Stub!");
    }

    public String getEditString() {
        throw new RuntimeException("Stub!");
    }

    public EditText getEditText() {
        throw new RuntimeException("Stub!");
    }

    public String getInputValue() {
        throw new RuntimeException("Stub!");
    }

    public TextView getMessageTextView() {
        throw new RuntimeException("Stub!");
    }

    public TextView getMessageTextView_Plain_Text() {
        throw new RuntimeException("Stub!");
    }

    public ViewGroup getRootView() {
        throw new RuntimeException("Stub!");
    }

    public int getRootViewHeight() {
        throw new RuntimeException("Stub!");
    }

    public boolean getSwitchState() {
        throw new RuntimeException("Stub!");
    }

    public Object getTag() {
        throw new RuntimeException("Stub!");
    }

    public TextView getTitleTextView() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasPreViewInDialog() {
        throw new RuntimeException("Stub!");
    }

    public void hideSoftInputFromWindow() {
        throw new RuntimeException("Stub!");
    }

    public void removePreviewView() {
        throw new RuntimeException("Stub!");
    }

    public void setBackground(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setBannerDrawable(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setBodyLayoutNoMargin() {
        throw new RuntimeException("Stub!");
    }

    public void setBrandColor(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setCheckBox(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setContentView(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setDisableApplyTheme(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void setEditLint(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setFirstDummyPreviewImage(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setImageOnClickListener(View.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setItems(String[] strArr, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessage(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessageCount(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessageMaxLine(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setMessageTextColor(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setMessageTextSize(float f) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessageWithEmo(String str, float f) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessageWithUrl(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessageWithoutAutoLink(CharSequence charSequence) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMsgMaxLineWithEnd(String str, int i, boolean z, boolean z2) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setNegativeButton(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setNegativeButtonContentDescription(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setOperateImage(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPositiveButton(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setPositiveButtonContentDescription(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPreviewFixDimension(int i, int i2) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPreviewImage(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPreviewImageScaleType(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void setProgress(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setProgressBarVisibility(int i) {
        throw new RuntimeException("Stub!");
    }

    protected void setSeperatorState() {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setSwitch(String str, boolean z, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setTag(Object obj) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setText(String str, int i) {
        throw new RuntimeException("Stub!");
    }

    public void setTextContentDescription(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setTextIsSelectable(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void setThemeId(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setTitle(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setTitleMultiLine() {
        throw new RuntimeException("Stub!");
    }

    public void setTitleTextColor(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setTitleWithEmo(CharSequence charSequence) {
        throw new RuntimeException("Stub!");
    }

    public void setUpdateTextColor(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setVideoFormat(boolean z, boolean z2, String str, String str2) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setView(View view) {
        throw new RuntimeException("Stub!");
    }

    public void show() {
        throw new RuntimeException("Stub!");
    }

    public static void showPermissionSettingDialog(Activity activity, String str, String str2) {
        throw new RuntimeException("Stub!");
    }

    public void setBannerDrawable(String str) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setCheckBox(String str, boolean z, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPreviewImage(Drawable drawable, boolean z, int i, boolean z2) {
        throw new RuntimeException("Stub!");
    }

    public static void showPermissionSettingDialog(Activity activity, String str, String str2, DialogInterface.OnClickListener onClickListener, DialogInterface.OnClickListener onClickListener2, DialogInterface.OnCancelListener onCancelListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessage(CharSequence charSequence) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog addView(View view, LinearLayout.LayoutParams layoutParams) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setNegativeButton(String str, DialogInterface.OnClickListener onClickListener, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPositiveButton(String str, int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setBannerDrawable(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public void setTitle(int i) {
        throw new RuntimeException("Stub!");
    }

    protected QQCustomDialog(Context context, boolean z, DialogInterface.OnCancelListener onCancelListener) {
        super(context, z, onCancelListener);
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setMessage(SpannableString spannableString) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPositiveButton(int i, DialogInterface.OnClickListener onClickListener, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setItems(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setNegativeButton(String str, int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setNegativeButton(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPositiveButton(String str, DialogInterface.OnClickListener onClickListener, boolean z) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog(Context context, int i) {
        super(context, i);
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPreviewImage(Drawable drawable, boolean z, int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialog setPositiveButton(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }
}
