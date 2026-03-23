package mqq.app;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import java.util.HashMap;
import mqq.observer.BusinessObserver;

public class NewIntent extends Intent {
    public static final String ACTION_ACCOUNT_CHANGED = "mqq.intent.action.ACCOUNT_CHANGED";
    public static final String ACTION_ACCOUNT_EXPIRED = "mqq.intent.action.ACCOUNT_EXPIRED";
    public static final String ACTION_ACCOUNT_KICKED = "mqq.intent.action.ACCOUNT_KICKED";
    public static final String ACTION_ACCOUNT_TIPS = "mqq.intent.action.ACCOUNT_TIPS";
    public static final String ACTION_CLONE_LOGIN = "mqq.intent.action.CLONE_LOGIN";
    public static final String ACTION_DEVLOCK_ROAM = "mqq.intent.action.DEVLOCK_ROAM";
    public static final String ACTION_EXIT = "mqq.intent.action.EXIT";
    public static final String ACTION_FORCE_LOGOUT = "mqq.intent.action.FORCE_LOGOUT";
    public static final String ACTION_GRAY = "mqq.intent.action.GRAY";
    public static final String ACTION_GRAY_APPLY = "mqq.intent.action.GRAY_APPLY";
    public static final String ACTION_GRAY_EXPIRED = "mqq.intent.action.GRAY_EXPIRED";
    public static final String ACTION_KICK_TO_LOGIN = "mqq.intent.action.KICK_TO_LOGIN";
    public static final String ACTION_LAUNCH = "mqq.intent.action.LAUNCH";
    public static final String ACTION_LOGIN = "mqq.intent.action.LOGIN";
    public static final String ACTION_LOGOUT = "mqq.intent.action.LOGOUT";
    public static final String ACTION_NOTICE_ON_PCACTIVE = "mqq.intent.action.NOTICE_ON_PCACTIVE";
    public static final String ACTION_NO_TEST_ENV_PERMISSION = "mqq.intent.action.NO_TEST_ENV_PERMISSION";
    public static final String ACTION_OVERLOAD_PUSH = "mqq.intent.action.OVERLOAD_PUSH";
    public static final String ACTION_PCACTIVE_TIPS = "mqq.intent.action.PCACTIVE_TIPS";
    public static final String ACTION_QRCODE_LOGIN_FINISH = "mqq.intent.action.QRCODE_LOGIN_FINISH";
    public static final String ACTION_SUSPEND = "mqq.intent.action.SUSPEND";
    public HashMap<String, Parcelable> intentMap;
    private BusinessObserver observer;
    public boolean runNow;
    public boolean withouLogin;

    public NewIntent(Context context, Class<? extends Servlet> cls) {
        super(context, cls);
        this.intentMap = null;
    }

    public BusinessObserver getObserver() {
        return this.observer;
    }

    public boolean isWithouLogin() {
        return this.withouLogin;
    }

    public void setObserver(BusinessObserver businessObserver) {
        this.observer = businessObserver;
    }

    public void setWithouLogin(boolean z) {
        this.withouLogin = z;
    }
}
