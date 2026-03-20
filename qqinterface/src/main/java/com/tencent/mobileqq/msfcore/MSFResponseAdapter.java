package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;
import java.util.HashMap;

public class MSFResponseAdapter {
    String mCmd;
    int mFailReason;
    boolean mHasReserveFields;
    boolean mIsBadNetwork;
    boolean mIsRecvFromMainConn;
    boolean mIsSecSigCmd;
    boolean mIsUinDyed;
    byte[] mRecvData;
    long mRecvTime;
    int mRecvWay;
    int mSecSignFlag;
    long mSendTime;
    int mSeq;
    String mSsoErrTips;
    int mSsoRet;
    int mState;
    HashMap<String, byte[]> mTransInfo;
    byte[] mTrpcErrMsg;
    int mTrpcFuncRetCode;
    int mTrpcRetcode;
    String mUid;
    String mUin;
    int mUinType;
    long mWriteSocketTime;

    public MSFResponseAdapter() {
        this.mUin = "";
        this.mUid = "";
        this.mCmd = "";
        this.mRecvData = new byte[0];
        this.mTrpcErrMsg = new byte[0];
        this.mTransInfo = new HashMap<>();
        this.mSsoErrTips = "";
    }

    @NonNull
    public String getCmd() {
        throw new RuntimeException("Stub !");
    }

    public int getFailReason() {
        throw new RuntimeException("Stub !");
    }

    public boolean getHasReserveFields() {
        throw new RuntimeException("Stub !");
    }

    public boolean getIsBadNetwork() {
        throw new RuntimeException("Stub !");
    }

    public boolean getIsRecvFromMainConn() {
        throw new RuntimeException("Stub !");
    }

    public boolean getIsSecSigCmd() {
        throw new RuntimeException("Stub !");
    }

    public boolean getIsUinDyed() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public byte[] getRecvData() {
        throw new RuntimeException("Stub !");
    }

    public long getRecvTime() {
        throw new RuntimeException("Stub !");
    }

    public int getRecvWay() {
        throw new RuntimeException("Stub !");
    }

    public int getSecSignFlag() {
        throw new RuntimeException("Stub !");
    }

    public long getSendTime() {
        throw new RuntimeException("Stub !");
    }

    public int getSeq() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public String getSsoErrTips() {
        throw new RuntimeException("Stub !");
    }

    public int getSsoRet() {
        throw new RuntimeException("Stub !");
    }

    public int getState() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public HashMap<String, byte[]> getTransInfo() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public byte[] getTrpcErrMsg() {
        throw new RuntimeException("Stub !");
    }

    public int getTrpcFuncRetCode() {
        throw new RuntimeException("Stub !");
    }

    public int getTrpcRetcode() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public String getUid() {
        throw new RuntimeException("Stub !");
    }

    @NonNull
    public String getUin() {
        throw new RuntimeException("Stub !");
    }

    public int getUinType() {
        throw new RuntimeException("Stub !");
    }

    public long getWriteSocketTime() {
        throw new RuntimeException("Stub !");
    }

    public void setCmd(String str) {
        throw new RuntimeException("Stub !");
    }

    public void setFailReason(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setHasReserveFields(boolean z) {
        throw new RuntimeException("Stub !");
    }

    public void setIsBadNetwork(boolean z) {
        throw new RuntimeException("Stub !");
    }

    public void setIsRecvFromMainConn(boolean z) {
        throw new RuntimeException("Stub !");
    }

    public void setIsSecSigCmd(boolean z) {
        throw new RuntimeException("Stub !");
    }

    public void setIsUinDyed(boolean z) {
        throw new RuntimeException("Stub !");
    }

    public void setRecvData(byte[] bArr) {
        throw new RuntimeException("Stub !");
    }

    public void setRecvTime(long j) {
        throw new RuntimeException("Stub !");
    }

    public void setRecvWay(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setSecSignFlag(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setSendTime(long j) {
        throw new RuntimeException("Stub !");
    }

    public void setSeq(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setSsoErrTips(String str) {
        throw new RuntimeException("Stub !");
    }

    public void setSsoRet(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setState(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setTransInfo(HashMap<String, byte[]> hashMap) {
        throw new RuntimeException("Stub !");
    }

    public void setTrpcErrMsg(byte[] bArr) {
        throw new RuntimeException("Stub !");
    }

    public void setTrpcFuncRetCode(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setTrpcRetcode(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setUid(String str) {
        throw new RuntimeException("Stub !");
    }

    public void setUin(String str) {
        throw new RuntimeException("Stub !");
    }

    public void setUinType(int i) {
        throw new RuntimeException("Stub !");
    }

    public void setWriteSocketTime(long j) {
        throw new RuntimeException("Stub !");
    }

    public MSFResponseAdapter(int i, int i2, @NonNull String str, @NonNull String str2, int i3, @NonNull String str3, @NonNull byte[] bArr, long j, long j2, long j3, int i4, boolean z, int i5, int i6, @NonNull byte[] bArr2, int i7, @NonNull HashMap<String, byte[]> hashMap, int i8, boolean z2, boolean z3, int i9, @NonNull String str4, boolean z4, boolean z5) {
        this.mUin = "";
        this.mUid = "";
        this.mCmd = "";
        this.mRecvData = new byte[0];
        this.mTrpcErrMsg = new byte[0];
        this.mState = i;
        this.mSeq = i2;
        this.mUin = str;
        this.mUid = str2;
        this.mUinType = i3;
        this.mCmd = str3;
        this.mRecvData = bArr;
        this.mSendTime = j;
        this.mWriteSocketTime = j2;
        this.mRecvTime = j3;
        this.mRecvWay = i4;
        this.mIsBadNetwork = z;
        this.mTrpcRetcode = i5;
        this.mTrpcFuncRetCode = i6;
        this.mTrpcErrMsg = bArr2;
        this.mFailReason = i7;
        this.mTransInfo = hashMap;
        this.mSecSignFlag = i8;
        this.mIsSecSigCmd = z2;
        this.mHasReserveFields = z3;
        this.mSsoRet = i9;
        this.mSsoErrTips = str4;
        this.mIsUinDyed = z4;
        this.mIsRecvFromMainConn = z5;
    }
}
