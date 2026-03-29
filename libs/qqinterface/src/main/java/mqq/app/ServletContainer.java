package mqq.app;

import android.content.Intent;
import com.tencent.qphone.base.remote.FromServiceMsg;

import java.util.concurrent.ConcurrentHashMap;

public class ServletContainer {
    final ConcurrentHashMap<String, Servlet> managedServlet = new ConcurrentHashMap<>();

    public ServletContainer(AppRuntime appRuntime) {
        throw new RuntimeException("Stub!");
    }

    public void registerServletCommand(String str, Servlet servlet) {
        throw new RuntimeException("Stub!");
    }

    public void destroy() {
        throw new RuntimeException("Stub!");
    }

    public void forward(AppRuntime appRuntime, Intent intent) {
        throw new RuntimeException("Stub!");
    }

    public void forwardForFail(NewIntent newIntent) {
        throw new RuntimeException("Stub!");
    }

    Servlet getServlet(String str) {
        throw new RuntimeException("Stub!");
    }

    public void notifyMSFServlet(Class<? extends MSFServlet> cls, FromServiceMsg fromServiceMsg) {
        throw new RuntimeException("Stub!");
    }
}
