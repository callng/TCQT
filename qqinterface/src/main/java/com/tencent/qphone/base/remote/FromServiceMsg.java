package com.tencent.qphone.base.remote;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class FromServiceMsg implements Parcelable, Cloneable {

    public FromServiceMsg(Parcel parcel) {
    }

    public static final Creator<FromServiceMsg> CREATOR = new Creator<>() {
        @Override
        public FromServiceMsg createFromParcel(Parcel in) {
            return new FromServiceMsg(in);
        }

        @Override
        public FromServiceMsg[] newArray(int size) {
            return new FromServiceMsg[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
    }

    @NonNull
    @Override
    public FromServiceMsg clone() {
        try {
            return (FromServiceMsg) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
