package mqq.app;

import com.tencent.qphone.base.remote.SimpleAccount;
import com.tencent.qphone.base.util.BaseApplication;
import java.util.List;

public abstract class MobileQQ extends BaseApplication {
    public static String PACKAGE_NAME = "com.tencent.mobileqq";

    public static MobileQQ getMobileQQ() {
        throw new RuntimeException("Stub!");
    }

    public String getQQProcessName() {
        throw new RuntimeException("Stub!");
    }

    public AppRuntime peekAppRuntime() {
        throw new RuntimeException("Stub!");
    }

    public void otherProcessExit(boolean keepMsfRegistered) {
        throw new RuntimeException("Stub!");
    }

    public void qqProcessExit(boolean stopMsfService) {
        throw new RuntimeException("Stub!");
    }

    public AppRuntime waitAppRuntime(BaseActivity baseActivity) {
        throw new RuntimeException("Stub!");
    }

    public AppRuntime waitAppRuntime() {
        throw new RuntimeException("Stub!");
    }

    public List<SimpleAccount> getAllAccounts() {
        throw new RuntimeException("Stub!");
    }

    public int getMsfConnectedNetType() {
        throw new RuntimeException("Stub!");
    }
}
