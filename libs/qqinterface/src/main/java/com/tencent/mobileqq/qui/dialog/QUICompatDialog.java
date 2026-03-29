package com.tencent.mobileqq.qui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class QUICompatDialog extends Dialog {
    public QUICompatDialog(@NonNull Context context) {
        super(context);
    }

    public static boolean isOpenCheckDialogDismiss() {
        throw new RuntimeException("Stub!");
    }

    public static boolean isOpenCheckDialogShow() {
        throw new RuntimeException("Stub!");
    }

    public static boolean isOpenLooperCheck() {
        throw new RuntimeException("Stub!");
    }

    public void updateNavigatorBarColor() {
        throw new RuntimeException("Stub!");
    }

    public void checkActivityStateAndDismiss() {
        throw new RuntimeException("Stub!");
    }

    public void checkActivityStateAndShow() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dismiss() {
        throw new RuntimeException("Stub!");
    }

    public boolean isActivityDestroyed() {
        throw new RuntimeException("Stub!");
    }

    public boolean isActivityFinishingOrDestroyed() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void show() {
        throw new RuntimeException("Stub!");
    }

    public QUICompatDialog(@NonNull Context context, int i) {
        super(context, i);
    }

    protected QUICompatDialog(@NonNull Context context, boolean z, @Nullable DialogInterface.OnCancelListener onCancelListener) {
        super(context, z, onCancelListener);
    }
}
