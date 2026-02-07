package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import java.util.HashMap;

public class MSFRequestAdapter {
    byte[] mA2;
    String mCmd;
    byte[] mD2;
    byte[] mD2key;
    byte[] mData;
    int mOptions;
    int mPacketType;
    int mPriority;
    int mResendNum;
    byte[] mSecDeviceToken;
    byte[] mSecExtra;
    byte[] mSecSign;
    int mSeq;
    int mTimeout;
    String mTraceInfo;
    HashMap<String, byte[]> mTransInfo;
    String mUid;
    String mUin;
    int mUinType;

    public MSFRequestAdapter() {
        this.mUin = "";
        this.mUid = "";
        this.mCmd = "";
        this.mTraceInfo = "";
        this.mData = new byte[0];
        this.mA2 = new byte[0];
        this.mD2 = new byte[0];
        this.mD2key = new byte[0];
        this.mTransInfo = new HashMap<>();
        this.mSecSign = new byte[0];
        this.mSecDeviceToken = new byte[0];
        this.mSecExtra = new byte[0];
    }

    @NonNull
    public byte[] getA2() {
        return this.mA2;
    }

    @NonNull
    public String getCmd() {
        return this.mCmd;
    }

    @NonNull
    public byte[] getD2() {
        return this.mD2;
    }

    @NonNull
    public byte[] getD2key() {
        return this.mD2key;
    }

    @NonNull
    public byte[] getData() {
        return this.mData;
    }

    public int getOptions() {
        return this.mOptions;
    }

    public int getPacketType() {
        return this.mPacketType;
    }

    public int getPriority() {
        return this.mPriority;
    }

    public int getResendNum() {
        return this.mResendNum;
    }

    @NonNull
    public byte[] getSecDeviceToken() {
        return this.mSecDeviceToken;
    }

    @NonNull
    public byte[] getSecExtra() {
        return this.mSecExtra;
    }

    @NonNull
    public byte[] getSecSign() {
        return this.mSecSign;
    }

    public int getSeq() {
        return this.mSeq;
    }

    public int getTimeout() {
        return this.mTimeout;
    }

    @NonNull
    public String getTraceInfo() {
        return this.mTraceInfo;
    }

    @NonNull
    public HashMap<String, byte[]> getTransInfo() {
        return this.mTransInfo;
    }

    @NonNull
    public String getUid() {
        return this.mUid;
    }

    @NonNull
    public String getUin() {
        return this.mUin;
    }

    public int getUinType() {
        return this.mUinType;
    }

    public void setA2(@NonNull byte[] bArr) {
        this.mA2 = bArr;
    }

    public void setCmd(@NonNull String str) {
        this.mCmd = str;
    }

    public void setD2(@NonNull byte[] bArr) {
        this.mD2 = bArr;
    }

    public void setD2key(@NonNull byte[] bArr) {
        this.mD2key = bArr;
    }

    public void setData(@NonNull byte[] bArr) {
        this.mData = bArr;
    }

    public void setOptions(int i) {
        this.mOptions = i;
    }

    public void setPacketType(int i) {
        this.mPacketType = i;
    }

    public void setPriority(int i) {
        this.mPriority = i;
    }

    public void setResendNum(int i) {
        this.mResendNum = i;
    }

    public void setSecDeviceToken(@NonNull byte[] bArr) {
        this.mSecDeviceToken = bArr;
    }

    public void setSecExtra(@NonNull byte[] bArr) {
        this.mSecExtra = bArr;
    }

    public void setSecSign(@NonNull byte[] bArr) {
        this.mSecSign = bArr;
    }

    public void setSeq(int i) {
        this.mSeq = i;
    }

    public void setTimeout(int i) {
        this.mTimeout = i;
    }

    public void setTraceInfo(@NonNull String str) {
        this.mTraceInfo = str;
    }

    public void setTransInfo(@NonNull HashMap<String, byte[]> hashMap) {
        this.mTransInfo = hashMap;
    }

    public void setUid(@NonNull String str) {
        this.mUid = str;
    }

    public void setUin(@NonNull String str) {
        this.mUin = str;
    }

    public void setUinType(int i) {
        this.mUinType = i;
    }

    public MSFRequestAdapter(int i, int i2, int i3, int i4, int i5, int i6, int i7, @NonNull String str, @NonNull String str2, @NonNull String str3, @NonNull String str4, @NonNull byte[] bArr, @NonNull byte[] bArr2, @NonNull byte[] bArr3, @NonNull byte[] bArr4, @NonNull HashMap<String, byte[]> hashMap, @NonNull byte[] bArr5, @NonNull byte[] bArr6, @NonNull byte[] bArr7) {
        this.mSeq = i;
        this.mTimeout = i2;
        this.mResendNum = i3;
        this.mOptions = i4;
        this.mUinType = i5;
        this.mPacketType = i6;
        this.mPriority = i7;
        this.mUin = str;
        this.mUid = str2;
        this.mCmd = str3;
        this.mTraceInfo = str4;
        this.mData = bArr;
        this.mA2 = bArr2;
        this.mD2 = bArr3;
        this.mD2key = bArr4;
        this.mTransInfo = hashMap;
        this.mSecSign = bArr5;
        this.mSecDeviceToken = bArr6;
        this.mSecExtra = bArr7;
    }
}
