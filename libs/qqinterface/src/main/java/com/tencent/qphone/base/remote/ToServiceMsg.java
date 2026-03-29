package com.tencent.qphone.base.remote;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import com.tencent.mobileqq.msf.sdk.MsfCommand;
import java.util.HashMap;

public class ToServiceMsg implements Parcelable {
    public static final Creator CREATOR = new Creator() {
        public ToServiceMsg createFromParcel(Parcel parcel) {
            return new ToServiceMsg(parcel);
        }

        public ToServiceMsg[] newArray(int i) {
            return new ToServiceMsg[i];
        }
    };
    private static final String KEY_FIRST_PKG_AFTER_CONN_OPEN = "key_first_pkg_after_conn_open";
    private static final String tag = "ToServiceMsg";
    private static final String version = "version";
    private int appId;
    private int appSeq;
    private HashMap<String, Object> attributes;

    @Deprecated
    public Bundle extraData;
    private boolean mIsSupportRetry;
    public boolean mSkipBinderWhenMarshall;
    private int mSsoVersion;
    private MsfCommand msfCommand;
    private boolean needResp;
    private boolean quickSendEnable;
    private int quickSendStrategy;
    private long sendTimeout;
    private String serviceCmd;
    private String serviceName;
    private int ssoSeq;
    private long timeout;
    private byte toVersion;
    private final HashMap<String, byte[]> transInfo;
    private String uin;
    private byte uinType;
    private byte[] wupBuffer;

    public ToServiceMsg(String str, String str2, String str3) {
        this.mSsoVersion = 0;
        this.sendTimeout = -1L;
        this.timeout = -1L;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.needResp = true;
        this.mIsSupportRetry = false;
        this.ssoSeq = -1;
        this.attributes = new HashMap<>(32);
        this.extraData = new Bundle();
        this.toVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.uinType = (byte) 0;
        this.quickSendEnable = false;
        this.quickSendStrategy = -1;
        this.mSkipBinderWhenMarshall = false;
        this.transInfo = new HashMap<>();
        this.serviceName = str;
        this.uin = str2;
        this.serviceCmd = str3;
        try {
            this.extraData.putByte(version, this.toVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Object addAttribute(String str, Object obj) {
        return this.attributes.put(str, obj);
    }

    public Object addTransInfo(String str, byte[] bArr) {
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

    public String getDestServiceId() {
        return this.serviceName;
    }

    public MsfCommand getMsfCommand() {
        return this.msfCommand;
    }

    public int getQuickSendStrategy() {
        return this.quickSendStrategy;
    }

    public int getRequestSsoSeq() {
        return this.ssoSeq;
    }

    public int getSSOVersion() {
        return this.mSsoVersion;
    }

    public long getSendTimeout() {
        return this.sendTimeout;
    }

    public String getServiceCmd() {
        return this.serviceCmd;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getShortStringForLog() {
        try {
            StringBuffer stringBuffer = new StringBuffer(256);
            stringBuffer.append("ToServiceMsg serviceCmd:");
            stringBuffer.append(this.serviceCmd);
            stringBuffer.append(", MSFCommand:");
            stringBuffer.append(this.msfCommand);
            stringBuffer.append(", ssoSeq:");
            stringBuffer.append(getRequestSsoSeq());
            stringBuffer.append(", appSeq:");
            stringBuffer.append(this.appSeq);
            stringBuffer.append(", needResp:");
            stringBuffer.append(this.needResp);
            if (!TextUtils.isEmpty(getTraceInfo())) {
                stringBuffer.append(", traceInfo:");
                stringBuffer.append(getTraceInfo());
            }
            return stringBuffer.toString();
        } catch (Exception e) {
            return "fsm getString error";
        }
    }

    public String getStringForLog() {
        try {
            StringBuffer stringBuffer = new StringBuffer(1024);
            stringBuffer.append("ToServiceMsg serviceCmd:");
            stringBuffer.append(this.serviceCmd);
            stringBuffer.append(", MSFCommand:");
            stringBuffer.append(this.msfCommand);
            stringBuffer.append(", ssoSeq:");
            stringBuffer.append(getRequestSsoSeq());
            stringBuffer.append(", appSeq:");
            stringBuffer.append(this.appSeq);
            stringBuffer.append(", uin:");
            stringBuffer.append(this.uin);
            stringBuffer.append(", timeout:");
            stringBuffer.append(this.timeout);
            stringBuffer.append(", needResp:");
            stringBuffer.append(this.needResp);
            stringBuffer.append(", len:");
            stringBuffer.append(this.wupBuffer.length);
            stringBuffer.append(", enableQuickSend:");
            stringBuffer.append(this.quickSendEnable);
            stringBuffer.append(", quickSendStrategy:");
            stringBuffer.append(this.quickSendStrategy);
            stringBuffer.append(", isSupportRetry:");
            stringBuffer.append(this.mIsSupportRetry);
            if (!TextUtils.isEmpty(getTraceInfo())) {
                stringBuffer.append(", traceInfo:");
                stringBuffer.append(getTraceInfo());
            }
            return stringBuffer.toString();
        } catch (Throwable th) {
            return "TSM getString error";
        }
    }

    public long getTimeout() {
        return this.timeout;
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

    public String getUin() {
        return this.uin;
    }

    public byte getUinType() {
        return this.uinType;
    }

    public byte[] getWupBuffer() {
        return this.wupBuffer;
    }

    public boolean isFastResendEnabled() {
        return ((Boolean) getAttribute("fastresend", Boolean.FALSE)).booleanValue();
    }

    public boolean isFirstPkgAfterConnOpen() {
        return ((Boolean) getAttribute(KEY_FIRST_PKG_AFTER_CONN_OPEN, Boolean.FALSE)).booleanValue();
    }

    public boolean isNeedCallback() {
        return this.needResp;
    }

    public boolean isNeedRemindSlowNetwork() {
        return ((Boolean) getAttribute("remind_slown_network", Boolean.FALSE)).booleanValue();
    }

    public boolean isQuickSendEnable() {
        return this.quickSendEnable;
    }

    public boolean isSupportRetry() {
        return this.mIsSupportRetry;
    }

    public void putWupBuffer(byte[] bArr) {
        this.wupBuffer = bArr;
    }

    public void readFromParcel(Parcel parcel) {
        try {
            this.appId = parcel.readInt();
            this.appSeq = parcel.readInt();
            this.serviceName = parcel.readString();
            this.uin = parcel.readString();
            this.uinType = parcel.readByte();
            this.serviceCmd = parcel.readString();
            this.timeout = parcel.readLong();
            this.extraData.clear();
            this.extraData.putAll(parcel.readBundle(Thread.currentThread().getContextClassLoader()));
            byte b = this.extraData.getByte(version);
            this.toVersion = b;
            if (b > 0) {
                this.msfCommand = (MsfCommand) parcel.readSerializable();
                this.sendTimeout = parcel.readLong();
                this.needResp = parcel.readByte() != 0;
                this.mIsSupportRetry = parcel.readByte() != 0;
                byte[] bArr = new byte[parcel.readInt()];
                this.wupBuffer = bArr;
                parcel.readByteArray(bArr);
                this.ssoSeq = parcel.readInt();
                this.attributes.clear();
                parcel.readMap(this.attributes, ToServiceMsg.class.getClassLoader());
                this.transInfo.clear();
                parcel.readMap(this.transInfo, ToServiceMsg.class.getClassLoader());
            }
            this.quickSendEnable = parcel.readInt() == 1;
            this.quickSendStrategy = parcel.readInt();
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

    public void setAttributes(HashMap<String, Object> hashMap) {
        this.attributes = hashMap;
    }

    public void setEnableFastResend(boolean z) {
        addAttribute("fastresend", Boolean.valueOf(z));
    }

    public void setFirstPkgAfterConnOpen(boolean z) {
        addAttribute(KEY_FIRST_PKG_AFTER_CONN_OPEN, Boolean.valueOf(z));
    }

    public void setIsSupportRetry(boolean z) {
        this.mIsSupportRetry = z;
    }

    public void setMsfCommand(MsfCommand msfCommand) {
        this.msfCommand = msfCommand;
    }

    public void setNeedCallback(boolean z) {
        this.needResp = z;
    }

    public void setNeedRemindSlowNetwork(boolean z) {
        addAttribute("remind_slown_network", Boolean.valueOf(z));
    }

    public void setQuickSend(boolean z, int i) {
        this.quickSendEnable = z;
        this.quickSendStrategy = i;
    }

    public void setRequestSsoSeq(int i) {
        this.ssoSeq = i;
    }

    public void setSSOVersion(int i) {
        this.mSsoVersion = i;
    }

    public void setSendTimeout(long j) {
        this.sendTimeout = j;
    }

    public void setServiceCmd(String str) {
        this.serviceCmd = str;
    }

    public void setServiceName(String str) {
        this.serviceName = str;
    }

    public void setTimeout(long j) {
        this.timeout = j;
    }

    public void setTraceInfo(String str) {
        this.attributes.put("tps_telemetry_tracing_info", str);
    }

    public void setUin(String str) {
        this.uin = str;
    }

    public void setUinType(byte b) {
        this.uinType = b;
    }

    public String toString() {
        try {
            StringBuffer stringBuffer = new StringBuffer(256);
            stringBuffer.append("ToServiceMsg msName:");
            stringBuffer.append(this.msfCommand);
            stringBuffer.append(" ssoSeq:");
            stringBuffer.append(getRequestSsoSeq());
            stringBuffer.append(" appId:");
            stringBuffer.append(this.appId);
            stringBuffer.append(" appSeq:");
            stringBuffer.append(this.appSeq);
            stringBuffer.append(" sName:");
            stringBuffer.append(this.serviceName);
            stringBuffer.append(" uin:");
            stringBuffer.append(" sCmd:");
            stringBuffer.append(this.serviceCmd);
            stringBuffer.append(" t:");
            stringBuffer.append(this.timeout);
            stringBuffer.append(" needResp:");
            stringBuffer.append(this.needResp);
            stringBuffer.append(" needQuickSend:");
            stringBuffer.append(this.quickSendEnable);
            stringBuffer.append(" strategy:");
            stringBuffer.append(this.quickSendStrategy);
            stringBuffer.append("IsSupportRetry");
            stringBuffer.append(this.mIsSupportRetry);
            return stringBuffer.toString();
        } catch (Throwable th) {
            return "TSM toString error";
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        try {
            parcel.writeInt(this.appId);
            parcel.writeInt(this.appSeq);
            parcel.writeString(this.serviceName);
            parcel.writeString(this.uin);
            parcel.writeByte(this.uinType);
            parcel.writeString(this.serviceCmd);
            parcel.writeLong(this.timeout);
            parcel.writeBundle(this.extraData);
            if (this.toVersion > 0) {
                parcel.writeSerializable(this.msfCommand);
                parcel.writeLong(this.sendTimeout);
                parcel.writeByte(this.needResp ? (byte) 1 : (byte) 0);
                parcel.writeByte(this.mIsSupportRetry ? (byte) 1 : (byte) 0);
                parcel.writeInt(this.wupBuffer.length);
                parcel.writeByteArray(this.wupBuffer);
                parcel.writeInt(this.ssoSeq);
                parcel.writeMap(this.attributes);
                parcel.writeMap(this.transInfo);
            }
            parcel.writeInt(this.quickSendEnable ? 1 : 0);
            parcel.writeInt(this.quickSendStrategy);
        } catch (RuntimeException e) {
            Log.d(tag, "writeToParcel RuntimeException", e);
            throw e;
        }
    }

    public <T> T getAttribute(String str, T t) {
        return !this.attributes.containsKey(str) ? t : (T) this.attributes.get(str);
    }

    public ToServiceMsg(Parcel parcel) {
        this.mSsoVersion = 0;
        this.sendTimeout = -1L;
        this.timeout = -1L;
        this.appSeq = -1;
        this.wupBuffer = new byte[0];
        this.needResp = true;
        this.mIsSupportRetry = false;
        this.ssoSeq = -1;
        this.attributes = new HashMap<>(32);
        this.extraData = new Bundle();
        this.toVersion = (byte) 1;
        this.msfCommand = MsfCommand.unknown;
        this.uinType = (byte) 0;
        this.quickSendEnable = false;
        this.quickSendStrategy = -1;
        this.mSkipBinderWhenMarshall = false;
        this.transInfo = new HashMap<>();
        readFromParcel(parcel);
    }
}
