package com.tencent.qphone.base.remote;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import com.tencent.mobileqq.msf.sdk.MsfCommand;
import java.util.HashMap;

public class FromServiceMsg implements Parcelable, Cloneable {
    public static final Creator CREATOR = new Creator() {
        public FromServiceMsg createFromParcel(Parcel parcel) {
            return new FromServiceMsg(parcel);
        }

        public FromServiceMsg[] newArray(int i) {
            return new FromServiceMsg[i];
        }
    };
    private static final String tag = "FromServiceMsg";
    private static final String version = "version";
    private int appId;
    private int appSeq;
    public HashMap<String, Object> attributes;
    private String errorMsg;

    @Deprecated
    public Bundle extraData;
    private int flag;
    private byte fromVersion;
    private boolean isColorLevel;
    private int mSsoEnc;
    private MsfCommand msfCommand;
    private byte[] msgCookie;
    private int resultCode;
    private String serviceCmd;
    private int ssoSeq;
    private final HashMap<String, byte[]> transInfo;
    private byte[] trpcRspErrorMsg;
    private int trpcRspFuncRetCode;
    private int trpcRspRetCode;
    private String uin;
    private int uinType;
    private byte[] wupBuffer;

    public FromServiceMsg() {
        this.errorMsg = "";
        this.mSsoEnc = 0;
        this.ssoSeq = -1;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.attributes = new HashMap<>(32);
        this.fromVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.msgCookie = new byte[0];
        this.trpcRspErrorMsg = new byte[0];
        this.transInfo = new HashMap<>();
        this.isColorLevel = false;
        this.uinType = 0;
        Bundle bundle = new Bundle();
        this.extraData = bundle;
        try {
            bundle.putByte(version, this.fromVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Object addAttribute(String str, Object obj) {
        return this.attributes.put(str, obj);
    }

    public synchronized Object addTransInfo(String str, byte[] bArr) {
        return this.transInfo.put(str, bArr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getAppId() {
        return this.appId;
    }

    public int getAppSeq() {
        return this.appSeq;
    }

    public Object getAttribute(String str) {
        return this.attributes.get(str);
    }

    public HashMap<String, Object> getAttributes() {
        return this.attributes;
    }

    public int getBusinessFailCode(int i) {
        int i2 = this.resultCode;
        return i2 == -1 ? i : i2;
    }

    public String getBusinessFailMsg() {
        return this.errorMsg;
    }

    public int getFlag() {
        return this.flag;
    }

    public byte getFromVersion() {
        return this.fromVersion;
    }

    public MsfCommand getMsfCommand() {
        return this.msfCommand;
    }

    public byte[] getMsgCookie() {
        return this.msgCookie;
    }

    public int getRequestSsoSeq() {
        return this.ssoSeq;
    }

    public int getResultCode() {
        return this.resultCode;
    }

    public String getServiceCmd() {
        return this.serviceCmd;
    }

    public String getShortStringForLog() {
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("FromServiceMsg serviceCmd:");
            sb.append(this.serviceCmd);
            sb.append(", msName:");
            sb.append(this.msfCommand);
            sb.append(", ssoSeq:");
            sb.append(getRequestSsoSeq());
            sb.append(", appSeq:");
            sb.append(this.appSeq);
            if (!TextUtils.isEmpty(getTraceInfo())) {
                sb.append(", traceInfo:");
                sb.append(getTraceInfo());
            }
            sb.append(", failCode:");
            sb.append(this.resultCode);
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "fsm getString error";
        }
    }

    public int getSsoEnc() {
        return this.mSsoEnc;
    }

    public String getStringForLog() {
        try {
            StringBuffer stringBuffer = new StringBuffer(1024);
            stringBuffer.append("FromServiceMsg serviceCmd:");
            stringBuffer.append(this.serviceCmd);
            stringBuffer.append(", MSFCommand:");
            stringBuffer.append(this.msfCommand);
            stringBuffer.append(", ssoSeq:");
            stringBuffer.append(getRequestSsoSeq());
            stringBuffer.append(", appSeq:");
            stringBuffer.append(this.appSeq);
            stringBuffer.append(", uin:");
            stringBuffer.append(this.uin);
            stringBuffer.append(", failCode:");
            stringBuffer.append(this.resultCode);
            stringBuffer.append(", errorMsg:");
            stringBuffer.append(this.errorMsg);
            stringBuffer.append(", len:");
            stringBuffer.append(this.wupBuffer.length);
            if (!TextUtils.isEmpty(getTraceInfo())) {
                stringBuffer.append(", traceInfo:");
                stringBuffer.append(getTraceInfo());
            }
            return stringBuffer.toString();
        } catch (Exception e) {
            return "fsm getString error";
        }
    }

    public String getTraceInfo() {
        if (!this.attributes.containsKey("tps_telemetry_tracing_info")) {
            return null;
        }
        Object obj = this.attributes.get("tps_telemetry_tracing_info");
        if (obj instanceof String) {
            return (String) obj;
        }
        return null;
    }

    public HashMap<String, byte[]> getTransInfo() {
        return this.transInfo;
    }

    public byte[] getTransInfoEntry(String str) {
        return this.transInfo.get(str);
    }

    public byte[] getTrpcRspErrorMsg() {
        return this.trpcRspErrorMsg;
    }

    public int getTrpcRspFuncRetCode() {
        return this.trpcRspFuncRetCode;
    }

    public int getTrpcRspRetCode() {
        return this.trpcRspRetCode;
    }

    public String getUin() {
        return this.uin;
    }

    public int getUinType() {
        return this.uinType;
    }

    public byte[] getWupBuffer() {
        return this.wupBuffer;
    }

    public boolean isColorLevel() {
        return this.isColorLevel;
    }

    public boolean isSuccess() {
        return this.resultCode == 1000;
    }

    public void putWupBuffer(byte[] bArr) {
        this.wupBuffer = bArr;
    }

    public void readFromParcel(Parcel parcel) {
        try {
            this.appId = parcel.readInt();
            this.appSeq = parcel.readInt();
            this.resultCode = parcel.readInt();
            this.uin = parcel.readString();
            this.serviceCmd = parcel.readString();
            this.extraData.clear();
            this.extraData = parcel.readBundle();
            this.attributes.clear();
            parcel.readMap(this.attributes, getClass().getClassLoader());
            if (this.extraData.getByte(version) > 0) {
                this.msfCommand = (MsfCommand) parcel.readSerializable();
                this.ssoSeq = parcel.readInt();
                this.errorMsg = parcel.readString();
                byte[] bArr = new byte[parcel.readInt()];
                this.wupBuffer = bArr;
                parcel.readByteArray(bArr);
                byte[] bArr2 = new byte[parcel.readInt()];
                this.msgCookie = bArr2;
                parcel.readByteArray(bArr2);
                this.flag = parcel.readInt();
                this.trpcRspRetCode = parcel.readInt();
                this.trpcRspFuncRetCode = parcel.readInt();
                byte[] bArr3 = new byte[parcel.readInt()];
                this.trpcRspErrorMsg = bArr3;
                parcel.readByteArray(bArr3);
                this.transInfo.clear();
                parcel.readMap(this.transInfo, FromServiceMsg.class.getClassLoader());
            }
        } catch (RuntimeException e) {
            Log.d(tag, "readFromParcel RuntimeException", e);
            throw e;
        }
    }

    public void setAppId(int i) {
        this.appId = i;
    }

    public void setAppSeq(int i) {
        this.appSeq = i;
    }

    public void setBusinessFail(int i) {
        this.resultCode = i;
    }

    public void setFlag(int i) {
        this.flag = i;
    }

    public void setFromVersion(byte b) {
        this.fromVersion = b;
    }

    public void setIsColorLevel(boolean z) {
        this.isColorLevel = z;
    }

    public void setMsfCommand(MsfCommand msfCommand) {
        this.msfCommand = msfCommand;
    }

    public void setMsgCookie(byte[] bArr) {
        this.msgCookie = bArr;
    }

    public void setMsgFail() {
        this.resultCode = 1001;
    }

    public void setMsgSuccess() {
        this.resultCode = 1000;
    }

    public void setRequestSsoSeq(int i) {
        this.ssoSeq = i;
    }

    public void setServiceCmd(String str) {
        this.serviceCmd = str;
    }

    public void setTraceInfo(String str) {
        this.attributes.put("tps_telemetry_tracing_info", str);
    }

    public void setTrpcRspErrorMsg(byte[] bArr) {
        this.trpcRspErrorMsg = bArr;
    }

    public void setTrpcRspFuncRetCode(int i) {
        this.trpcRspFuncRetCode = i;
    }

    public void setTrpcRspRetCode(int i) {
        this.trpcRspRetCode = i;
    }

    public void setUin(String str) {
        this.uin = str;
    }

    public void setUinType(int i) {
        this.uinType = i;
    }

    public String toString() {
        try {
            int length = this.msfCommand.toString().length() + 22 + 8 + 10 + 10 + 10 + 10 + this.errorMsg.length() + 5 + 10 + 12;
            String str = this.serviceCmd;
            StringBuilder sb = new StringBuilder(length + (str == null ? 4 : str.length()) + 7 + 10 + 8 + 10 + 10);
            sb.append("FromServiceMsg msName:");
            sb.append(this.msfCommand);
            sb.append(" ssoSeq:");
            sb.append(getRequestSsoSeq());
            sb.append(" failCode:");
            sb.append(this.resultCode);
            sb.append(" errorMsg:");
            sb.append(this.errorMsg);
            sb.append(" uin:");
            sb.append(this.uin);
            sb.append(" serviceCmd:");
            sb.append(this.serviceCmd);
            sb.append(" appId:");
            sb.append(this.appId);
            sb.append(" appSeq:");
            sb.append(this.appSeq);
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "fsm toString error";
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        try {
            parcel.writeInt(this.appId);
            parcel.writeInt(this.appSeq);
            parcel.writeInt(this.resultCode);
            parcel.writeString(this.uin);
            parcel.writeString(this.serviceCmd);
            parcel.writeBundle(this.extraData);
            parcel.writeMap(this.attributes);
            if (this.fromVersion > 0) {
                parcel.writeSerializable(this.msfCommand);
                parcel.writeInt(this.ssoSeq);
                parcel.writeString(this.errorMsg);
                parcel.writeInt(this.wupBuffer.length);
                parcel.writeByteArray(this.wupBuffer);
                parcel.writeInt(this.msgCookie.length);
                parcel.writeByteArray(this.msgCookie);
                parcel.writeInt(this.flag);
                parcel.writeInt(this.trpcRspRetCode);
                parcel.writeInt(this.trpcRspFuncRetCode);
                parcel.writeInt(this.trpcRspErrorMsg.length);
                parcel.writeByteArray(this.trpcRspErrorMsg);
                parcel.writeMap(this.transInfo);
            }
        } catch (RuntimeException e) {
            Log.d(tag, "writeToParcel RuntimeException", e);
            throw e;
        }
    }

    public <T> T getAttribute(String str, T t) {
        return !this.attributes.containsKey(str) ? t : (T) this.attributes.get(str);
    }

    public int getBusinessFailCode() {
        return this.resultCode;
    }

    public void setBusinessFail(int i, String str) {
        this.resultCode = i;
        this.errorMsg = str;
    }

    public void setBusinessFail(int i, int i2, String str) {
        this.resultCode = i2;
        this.errorMsg = str;
    }

    public FromServiceMsg(String str, String str2) {
        this(-1, -1, str, str2);
        try {
            this.extraData.putByte(version, this.fromVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FromServiceMsg(int i, int i2, String str, String str2) {
        this.errorMsg = "";
        this.mSsoEnc = 0;
        this.ssoSeq = -1;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.attributes = new HashMap<>(32);
        this.fromVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.msgCookie = new byte[0];
        this.trpcRspErrorMsg = new byte[0];
        this.transInfo = new HashMap<>();
        this.isColorLevel = false;
        this.uinType = 0;
        Bundle bundle = new Bundle();
        this.extraData = bundle;
        this.resultCode = 1001;
        this.appId = i;
        this.appSeq = i2;
        this.uin = str;
        this.serviceCmd = str2;
        try {
            bundle.putByte(version, this.fromVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FromServiceMsg(int i, int i2, String str, String str2, byte[] bArr) {
        this.errorMsg = "";
        this.mSsoEnc = 0;
        this.ssoSeq = -1;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.attributes = new HashMap<>(32);
        this.fromVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.msgCookie = new byte[0];
        this.trpcRspErrorMsg = new byte[0];
        this.transInfo = new HashMap<>();
        this.isColorLevel = false;
        this.uinType = 0;
        Bundle bundle = new Bundle();
        this.extraData = bundle;
        this.resultCode = 1001;
        this.appId = i;
        this.appSeq = i2;
        this.uin = str;
        this.serviceCmd = str2;
        this.msgCookie = bArr;
        try {
            bundle.putByte(version, this.fromVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FromServiceMsg(Parcel parcel) {
        this.errorMsg = "";
        this.mSsoEnc = 0;
        this.ssoSeq = -1;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.attributes = new HashMap<>(32);
        this.fromVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.msgCookie = new byte[0];
        this.trpcRspErrorMsg = new byte[0];
        this.transInfo = new HashMap<>();
        this.isColorLevel = false;
        this.uinType = 0;
        this.extraData = new Bundle();
        readFromParcel(parcel);
    }
}
