package com.tencent.qqlive.module.videoreport.inject.dialog;

import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tencent.mobileqq.qui.dialog.QUICompatDialog;

public class ReportDialog extends QUICompatDialog {
    public ReportDialog(@NonNull Context context) {
        super(context);
    }

    protected void onStop() {
        super.onStop();
    }

    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
    }

    public ReportDialog(@NonNull Context context, int i) {
        super(context, i);
    }

    protected ReportDialog(@NonNull Context context, boolean z, @Nullable DialogInterface.OnCancelListener onCancelListener) {
        super(context, z, onCancelListener);
    }
}
