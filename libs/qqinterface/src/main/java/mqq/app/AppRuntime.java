package mqq.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.tencent.qphone.base.remote.FromServiceMsg;
import com.tencent.qphone.base.remote.SimpleAccount;
import com.tencent.qphone.base.remote.ToServiceMsg;
import com.tencent.qphone.base.util.BaseApplication;
import mqq.app.api.IRuntimeService;
import mqq.manager.Manager;
import mqq.observer.BusinessObserver;

public abstract class AppRuntime {
    public static final int ACCOUNT_MANAGER = 0;
    public static final int END_UN_LOGIN_MANAGER = 4;
    public static final int LOGIN_AUTO = 2;
    public static final int LOGIN_MANUAL = 1;
    public static final int LOGIN_UNINIT = 0;
    public static final String PROCESS = "process";
    public static final int PUSH_MANAGER = 4;
    public static final int SERVER_CONFIG_MANAGER = 3;
    public static final String SP_UIN_TO_UID = "MSF_SP_UIN_TO_UID";
    protected static final String TAG = "mqq";
    public static final int TICKET_MANAGER = 2;
    public static final int TYPE_CREATENEWRUNTIME_CHANGUIN_LOGIN = 4;
    public static final int TYPE_CREATENEWRUNTIME_DIRECT_LOGIN = 1;
    public static final int TYPE_CREATENEWRUNTIME_DIRECT_NET_LOGIN = 3;
    public static final int TYPE_CREATENEWRUNTIME_DOINIT = 5;
    public static final int TYPE_CREATENEWRUNTIME_SWITCHACCOUNT = 2;
    public static final int WTLOGIN_MANAGER = 1;

    public enum Status {
        online(11),
        offline(21),
        away(31),
        invisiable(41),
        busy(50),
        qme(60),
        dnd(70),
        receiveofflinemsg(95);

        private final int value;

        Status(int i) {
            this.value = i;
        }

        public static Status build(int i) {
            if (i == 11) {
                return online;
            }
            if (i == 21) {
                return offline;
            }
            if (i == 31) {
                return away;
            }
            if (i == 41) {
                return invisiable;
            }
            if (i == 50) {
                return busy;
            }
            if (i == 60) {
                return qme;
            }
            if (i == 70) {
                return dnd;
            }
            if (i != 95) {
                return null;
            }
            return receiveofflinemsg;
        }

        public int getValue() {
            return this.value;
        }
    }

    public static class KickParams {
        public Object fromServiceMsg;
        boolean isSameDevice;
        boolean isTokenExpired;
        public Object toServiceMsg;

        public KickParams(ToServiceMsg toServiceMsg, FromServiceMsg fromServiceMsg, boolean z, boolean z2) {
            this.toServiceMsg = toServiceMsg;
            this.fromServiceMsg = fromServiceMsg;
            this.isTokenExpired = z;
            this.isSameDevice = z2;
        }
    }

    public <T extends BusinessObserver> void addDirectNotifyObserver(Class<T> cls, int i) {
        throw new RuntimeException("Stub!");
    }

    public <T extends BusinessObserver> void addWithoutPostObserver(Class<T> cls) {
        throw new RuntimeException("Stub!");
    }

    protected boolean canAutoLogin(String str) {
        throw new RuntimeException("Stub!");
    }

    public abstract BaseApplication getApp();

    public AppRuntime getAppRuntime(String str) {
        return null;
    }

    public MobileQQ getApplication() {
        throw new RuntimeException("Stub!");
    }

    public Context getApplicationContext() {
        throw new RuntimeException("Stub!");
    }

    public int getCurAccLoginType() {
        throw new RuntimeException("Stub!");
    }

    public Intent getKickIntent() {
        throw new RuntimeException("Stub!");
    }

    /**
     * 0: AccountManagerImpl
     * 1: WtloginManagerImpl
     * 2: TicketManagerImpl
     */
    public Manager getManager(int i) {
        throw new RuntimeException("Stub!");
    }

    public final SharedPreferences getPreferences() {
        throw new RuntimeException("Stub!");
    }

    public ToServiceMsg getRegisterCmdCallMsg(Intent intent) {
        throw new RuntimeException("Stub!");
    }

    public int getRunningModuleSize() {
        throw new RuntimeException("Stub!");
    }

    public byte[] getUinSign() {
        throw new RuntimeException("Stub!");
    }

    public void logout(boolean z) {
        logout(Constants.LogoutReason.user, z);
    }

    public void ntTriggerLogout(Constants.LogoutReason logoutReason) {
        logout(logoutReason, true);
    }

    protected void logout(Constants.LogoutReason logoutReason, boolean z) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public <T extends IRuntimeService> T getRuntimeService(@NonNull Class<T> cls, String str) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public <T extends IRuntimeService> T getRuntimeServiceIPCSync(@NonNull Class<T> cls, String str) {
        throw new UnsupportedOperationException();
    }

    public boolean isModuleRunning(String str) {
        throw new RuntimeException("Stub!");
    }

    public boolean isRunning() {
        throw new RuntimeException("Stub!");
    }

    public void switchAccount(SimpleAccount simpleAccount, String process) {
        throw new RuntimeException("Stub!");
    }

    public void kick(KickParams kickParams) {
        throw new RuntimeException("Stub!");
    }

    public String getAccount() {
        throw new RuntimeException("Stub!");
    }

    public abstract String getCurrentAccountUin();

    public String getCurrentUin() {
        throw new RuntimeException("Stub!");
    }

    public String getCurrentUid() {
        throw new RuntimeException("Stub!");
    }

    public long getLongAccountUin() {
        throw new RuntimeException("Stub!");
    }

    public boolean isLogin() {
        throw new RuntimeException("Stub!");
    }
}
