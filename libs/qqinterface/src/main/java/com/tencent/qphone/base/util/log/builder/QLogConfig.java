package com.tencent.qphone.base.util.log.builder;

import android.content.Context;
import com.tencent.qphone.base.util.report.IQLogReport;
import com.tencent.qphone.base.util.report.firebase.IFirebaseLogReport;

public class QLogConfig {
    private final Context mAppContext;
    private final String mBuildNumber;
    private final long mDelayInit;
    private final IFirebaseLogReport mFirebaseLogReport;
    private final byte mForceCallbackAndroidLogLevel;
    private final boolean mIs64Bit;
    private final boolean mIsDebug;
    private final boolean mIsGray;
    private final boolean mIsPublish;
    private final boolean mIsUseXlog;
    private final String mLogPath;
    private final String mPackageName;
    private final String mProcessName;
    private final String mQLogConfigBeanSwitchKey;
    private final IQLogReport mQLogReport;
    private final boolean useNewQLog;

    public static class Builder {
        Context appContext;
        public boolean useNewQLog;
        boolean isDebug = false;
        boolean isPublish = false;
        byte forceCallbackAndroidLogLevel = Byte.MAX_VALUE;
        boolean isGray = false;
        boolean isUseXlog = false;
        String processName = "";
        String packageName = "";
        String buildNumber = "";
        long delayInit = 0;
        boolean is64Bit = true;
        String qLogConfigBeanSwitchKey = "";
        IQLogReport qLogReport = null;
        IFirebaseLogReport firebaseLogReport = null;
        String logPath = "";

        public QLogConfig build() {
            return new QLogConfig(this);
        }

        public Builder setAppContext(Context context) {
            this.appContext = context;
            return this;
        }

        public Builder setBuildNumber(String str) {
            this.buildNumber = str;
            return this;
        }

        public Builder setDebug(boolean z) {
            this.isDebug = z;
            return this;
        }

        public Builder setDelayInit(long j) {
            this.delayInit = j;
            return this;
        }

        public Builder setFirebaseLogReport(IFirebaseLogReport iFirebaseLogReport) {
            this.firebaseLogReport = iFirebaseLogReport;
            return this;
        }

        public Builder setForceCallback(byte b) {
            this.forceCallbackAndroidLogLevel = b;
            return this;
        }

        public Builder setGray(boolean z) {
            this.isGray = z;
            return this;
        }

        public Builder setIs64Bit(boolean z) {
            this.is64Bit = z;
            return this;
        }

        public Builder setLogPath(String str) {
            this.logPath = str;
            return this;
        }

        public Builder setPackageName(String str) {
            this.packageName = str;
            return this;
        }

        public Builder setProcessName(String str) {
            this.processName = str;
            return this;
        }

        public Builder setPublish(boolean z) {
            this.isPublish = z;
            return this;
        }

        public Builder setQLogConfigBeanSwitchKey(String str) {
            this.qLogConfigBeanSwitchKey = str;
            return this;
        }

        public Builder setQLogReport(IQLogReport iQLogReport) {
            this.qLogReport = iQLogReport;
            return this;
        }

        public Builder setUseNewQLog(boolean z) {
            this.useNewQLog = z;
            return this;
        }

        public Builder setUseXlog(boolean z) {
            this.isUseXlog = z;
            return this;
        }
    }

    public Context getAppContext() {
        return this.mAppContext;
    }

    public String getBuildNumber() {
        return this.mBuildNumber;
    }

    public long getDelayInit() {
        return this.mDelayInit;
    }

    public IFirebaseLogReport getFirebaseLogReport() {
        return this.mFirebaseLogReport;
    }

    public byte getForceCallbackAndroidLogLevel() {
        return this.mForceCallbackAndroidLogLevel;
    }

    public String getLogPath() {
        return this.mLogPath;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public String getProcessName() {
        return this.mProcessName;
    }

    public String getQLogConfigBeanSwitchKey() {
        return this.mQLogConfigBeanSwitchKey;
    }

    public IQLogReport getQLogReport() {
        return this.mQLogReport;
    }

    public boolean isDebug() {
        return this.mIsDebug;
    }

    public boolean isGray() {
        return this.mIsGray;
    }

    public boolean isIs64Bit() {
        return this.mIs64Bit;
    }

    public boolean isPublish() {
        return this.mIsPublish;
    }

    public boolean isUseNewQLog() {
        return this.useNewQLog;
    }

    public boolean isUseXlog() {
        return this.mIsUseXlog;
    }

    private QLogConfig(Builder builder) {
        this.mAppContext = builder.appContext;
        this.mIsDebug = builder.isDebug;
        this.mIsPublish = builder.isPublish;
        this.mForceCallbackAndroidLogLevel = builder.forceCallbackAndroidLogLevel;
        this.mIsGray = builder.isGray;
        this.mIsUseXlog = builder.isUseXlog;
        this.mProcessName = builder.processName;
        this.mPackageName = builder.packageName;
        this.mBuildNumber = builder.buildNumber;
        this.mDelayInit = builder.delayInit;
        this.mIs64Bit = builder.is64Bit;
        this.mQLogConfigBeanSwitchKey = builder.qLogConfigBeanSwitchKey;
        this.mQLogReport = builder.qLogReport;
        this.mFirebaseLogReport = builder.firebaseLogReport;
        this.useNewQLog = builder.useNewQLog;
        this.mLogPath = builder.logPath;
    }
}
