package androidx.appcompat.app;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.view.KeyEvent;
import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

public class AlertDialog {

    static final int LAYOUT_HINT_NONE = 0;
    static final int LAYOUT_HINT_SIDE = 1;

    protected AlertDialog(@NonNull Context context) {
        this(context, LAYOUT_HINT_NONE);
    }

    static int resolveDialogTheme(@NonNull Context context, @StyleRes int i) {
        throw new RuntimeException("Stub!");
    }

    public Button getButton(int i) {
        throw new RuntimeException("Stub!");
    }

    public ListView getListView() {
        throw new RuntimeException("Stub!");
    }

    protected void onCreate(Bundle bundle) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyUp(int i, KeyEvent keyEvent) {
        throw new RuntimeException("Stub!");
    }

    public void setButton(int i, CharSequence charSequence, Message message) {
        throw new RuntimeException("Stub!");
    }

    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
    void setButtonPanelLayoutHint(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setCustomTitle(View view) {
        throw new RuntimeException("Stub!");
    }

    public void setIcon(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setIconAttribute(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setMessage(CharSequence charSequence) {
        throw new RuntimeException("Stub!");
    }

    public void setTitle(CharSequence charSequence) {
        throw new RuntimeException("Stub!");
    }

    public void setView(View view) {
        throw new RuntimeException("Stub!");
    }

    protected AlertDialog(@NonNull Context context, @StyleRes int i) {
        throw new RuntimeException("Stub!");
    }

    public void setButton(int i, CharSequence charSequence, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    public void setIcon(Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public void setView(View view, int i, int i2, int i3, int i4) {
        throw new RuntimeException("Stub!");
    }

    public void setButton(int i, CharSequence charSequence, Drawable drawable, DialogInterface.OnClickListener onClickListener) {
        throw new RuntimeException("Stub!");
    }

    protected AlertDialog(@NonNull Context context, boolean z, @Nullable DialogInterface.OnCancelListener onCancelListener) {
        throw new RuntimeException("Stub!");
    }

    public void show() {
        throw new RuntimeException("Stub!");
    }

    public static class Builder {

        public Builder(@NonNull Context context) {
            this(context, AlertDialog.resolveDialogTheme(context, 0));
        }

        @NonNull
        public AlertDialog create() {
            throw new RuntimeException("Stub!");
        }

        @NonNull
        public Context getContext() {
            throw new RuntimeException("Stub!");
        }

        public Builder setAdapter(ListAdapter listAdapter, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setCancelable(boolean z) {
            throw new RuntimeException("Stub!");
        }

        public Builder setCursor(Cursor cursor, DialogInterface.OnClickListener onClickListener, String str) {
            throw new RuntimeException("Stub!");
        }

        public Builder setCustomTitle(@Nullable View view) {
            throw new RuntimeException("Stub!");
        }

        public Builder setIcon(@DrawableRes int i) {
            throw new RuntimeException("Stub!");
        }

        public Builder setIconAttribute(@AttrRes int i) {
            throw new RuntimeException("Stub!");
        }

        @Deprecated
        public Builder setInverseBackgroundForced(boolean z) {
            throw new RuntimeException("Stub!");
        }

        public Builder setItems(@ArrayRes int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setMessage(@StringRes int i) {
            throw new RuntimeException("Stub!");
        }

        public Builder setMultiChoiceItems(@ArrayRes int i, boolean[] zArr, DialogInterface.OnMultiChoiceClickListener onMultiChoiceClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNegativeButton(@StringRes int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNegativeButtonIcon(Drawable drawable) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNeutralButton(@StringRes int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNeutralButtonIcon(Drawable drawable) {
            throw new RuntimeException("Stub!");
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setOnItemSelectedListener(AdapterView.OnItemSelectedListener onItemSelectedListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setOnKeyListener(DialogInterface.OnKeyListener onKeyListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setPositiveButton(@StringRes int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setPositiveButtonIcon(Drawable drawable) {
            throw new RuntimeException("Stub!");
        }

        @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
        public Builder setRecycleOnMeasureEnabled(boolean z) {
            throw new RuntimeException("Stub!");
        }

        public Builder setSingleChoiceItems(@ArrayRes int i, int i2, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setTitle(@StringRes int i) {
            throw new RuntimeException("Stub!");
        }

        public Builder setView(int i) {
            throw new RuntimeException("Stub!");
        }

        public AlertDialog show() {
            throw new RuntimeException("Stub!");
        }

        public Builder(@NonNull Context context, @StyleRes int i) {
            throw new RuntimeException("Stub!");
        }

        public Builder setIcon(@Nullable Drawable drawable) {
            throw new RuntimeException("Stub!");
        }

        public Builder setMessage(@Nullable CharSequence charSequence) {
            throw new RuntimeException("Stub!");
        }

        public Builder setTitle(@Nullable CharSequence charSequence) {
            throw new RuntimeException("Stub!");
        }

        public Builder setItems(CharSequence[] charSequenceArr, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNegativeButton(CharSequence charSequence, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setNeutralButton(CharSequence charSequence, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setPositiveButton(CharSequence charSequence, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setView(View view) {
            throw new RuntimeException("Stub!");
        }

        public Builder setMultiChoiceItems(CharSequence[] charSequenceArr, boolean[] zArr, DialogInterface.OnMultiChoiceClickListener onMultiChoiceClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setSingleChoiceItems(Cursor cursor, int i, String str, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
        @Deprecated
        public Builder setView(View view, int i, int i2, int i3, int i4) {
            throw new RuntimeException("Stub!");
        }

        public Builder setMultiChoiceItems(Cursor cursor, String str, String str2, DialogInterface.OnMultiChoiceClickListener onMultiChoiceClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setSingleChoiceItems(CharSequence[] charSequenceArr, int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }

        public Builder setSingleChoiceItems(ListAdapter listAdapter, int i, DialogInterface.OnClickListener onClickListener) {
            throw new RuntimeException("Stub!");
        }
    }
}
