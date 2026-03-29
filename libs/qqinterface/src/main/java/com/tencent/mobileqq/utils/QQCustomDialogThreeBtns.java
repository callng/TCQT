package com.tencent.mobileqq.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.tencent.qqlive.module.videoreport.inject.dialog.ReportDialog;

public class QQCustomDialogThreeBtns extends ReportDialog {
    private BaseAdapter adapter;
    LinearLayout bodyLayout;
    LayoutInflater inflater;
    String[] items;
    TextView lBtn;
    TextView mBtn;
    DialogInterface.OnClickListener onArrayItemClick;
    TextView previewImage;
    TextView rBtn;
    TextView text;
    TextView title;

    public QQCustomDialogThreeBtns(Context context) {
        super(context);
    }

    public QQCustomDialogThreeBtns addView(View view) {
        throw new RuntimeException("Stub!");
    }

    protected int customWhichToCallBack(int i) {
        throw new RuntimeException("Stub!");
    }

    public void dismiss() {
        throw new RuntimeException("Stub!");
    }

    public void setContentView(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setItems(String[] strArr, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setLeftButton(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setMessage(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setMessageMaxLine(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setMiddleButton(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setPreviewImage(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setRightButton(String str, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setText(String str, int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setTitle(String str) {
        throw new RuntimeException("Stub!");
    }

    protected QQCustomDialogThreeBtns(Context context, boolean z, DialogInterface.OnCancelListener onCancelListener) {
        super(context, z, onCancelListener);
    }

    public QQCustomDialogThreeBtns setMessage(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setTitle(int i) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns(Context context, int i) {
        super(context, i);
    }

    public QQCustomDialogThreeBtns setLeftButton(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setMiddleButton(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setRightButton(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public QQCustomDialogThreeBtns setItems(int i, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }
}
