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
        return this.mCmd;
    }

    public int getFailReason() {
        return this.mFailReason;
    }

    public boolean getHasReserveFields() {
        return this.mHasReserveFields;
    }

    public boolean getIsBadNetwork() {
        return this.mIsBadNetwork;
    }

    public boolean getIsRecvFromMainConn() {
        return this.mIsRecvFromMainConn;
    }

    public boolean getIsSecSigCmd() {
        return this.mIsSecSigCmd;
    }

    public boolean getIsUinDyed() {
        return this.mIsUinDyed;
    }

    @NonNull
    public byte[] getRecvData() {
        return this.mRecvData;
    }

    public long getRecvTime() {
        return this.mRecvTime;
    }

    public int getRecvWay() {
        return this.mRecvWay;
    }

    public int getSecSignFlag() {
        return this.mSecSignFlag;
    }

    public long getSendTime() {
        return this.mSendTime;
    }

    public int getSeq() {
        return this.mSeq;
    }

    @NonNull
    public String getSsoErrTips() {
        return this.mSsoErrTips;
    }

    public int getSsoRet() {
        return this.mSsoRet;
    }

    public int getState() {
        return this.mState;
    }

    @NonNull
    public HashMap<String, byte[]> getTransInfo() {
        return this.mTransInfo;
    }

    @NonNull
    public byte[] getTrpcErrMsg() {
        return this.mTrpcErrMsg;
    }

    public int getTrpcFuncRetCode() {
        return this.mTrpcFuncRetCode;
    }

    public int getTrpcRetcode() {
        return this.mTrpcRetcode;
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

    public long getWriteSocketTime() {
        return this.mWriteSocketTime;
    }

    public void setCmd(@NonNull String str) {
        this.mCmd = str;
    }

    public void setFailReason(int i) {
        this.mFailReason = i;
    }

    public void setHasReserveFields(boolean z) {
        this.mHasReserveFields = z;
    }

    public void setIsBadNetwork(boolean z) {
        this.mIsBadNetwork = z;
    }

    public void setIsRecvFromMainConn(boolean z) {
        this.mIsRecvFromMainConn = z;
    }

    public void setIsSecSigCmd(boolean z) {
        this.mIsSecSigCmd = z;
    }

    public void setIsUinDyed(boolean z) {
        this.mIsUinDyed = z;
    }

    public void setRecvData(@NonNull byte[] bArr) {
        this.mRecvData = bArr;
    }

    public void setRecvTime(long j) {
        this.mRecvTime = j;
    }

    public void setRecvWay(int i) {
        this.mRecvWay = i;
    }

    public void setSecSignFlag(int i) {
        this.mSecSignFlag = i;
    }

    public void setSendTime(long j) {
        this.mSendTime = j;
    }

    public void setSeq(int i) {
        this.mSeq = i;
    }

    public void setSsoErrTips(@NonNull String str) {
        this.mSsoErrTips = str;
    }

    public void setSsoRet(int i) {
        this.mSsoRet = i;
    }

    public void setState(int i) {
        this.mState = i;
    }

    public void setTransInfo(@NonNull HashMap<String, byte[]> hashMap) {
        this.mTransInfo = hashMap;
    }

    public void setTrpcErrMsg(@NonNull byte[] bArr) {
        this.mTrpcErrMsg = bArr;
    }

    public void setTrpcFuncRetCode(int i) {
        this.mTrpcFuncRetCode = i;
    }

    public void setTrpcRetcode(int i) {
        this.mTrpcRetcode = i;
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

    public void setWriteSocketTime(long j) {
        this.mWriteSocketTime = j;
    }

    @Override
    public String toString() {
        return "MSFResponseAdapter{mState=" + this.mState + ",mSeq=" + this.mSeq + ",mUin=" + this.mUin + ",mUid=" + this.mUid + ",mUinType=" + this.mUinType + ",mCmd=" + this.mCmd + ",mRecvData=" + this.mRecvData + ",mSendTime=" + this.mSendTime + ",mWriteSocketTime=" + this.mWriteSocketTime + ",mRecvTime=" + this.mRecvTime + ",mRecvWay=" + this.mRecvWay + ",mIsBadNetwork=" + this.mIsBadNetwork + ",mTrpcRetcode=" + this.mTrpcRetcode + ",mTrpcFuncRetCode=" + this.mTrpcFuncRetCode + ",mTrpcErrMsg=" + this.mTrpcErrMsg + ",mFailReason=" + this.mFailReason + ",mTransInfo=" + this.mTransInfo + ",mSecSignFlag=" + this.mSecSignFlag + ",mIsSecSigCmd=" + this.mIsSecSigCmd + ",mHasReserveFields=" + this.mHasReserveFields + ",mSsoRet=" + this.mSsoRet + ",mSsoErrTips=" + this.mSsoErrTips + ",mIsUinDyed=" + this.mIsUinDyed + ",mIsRecvFromMainConn=" + this.mIsRecvFromMainConn + ",}";
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
