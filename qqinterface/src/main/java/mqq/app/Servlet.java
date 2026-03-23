package mqq.app;

import android.content.Intent;
import android.os.Bundle;
import mqq.observer.BusinessObserver;

public abstract class Servlet {
    private ServletContainer container;
    private AppRuntime mAppRuntime;

    public AppRuntime getAppRuntime() {
        return this.mAppRuntime;
    }

    ServletContainer getServletContainer() {
        return this.container;
    }

    void init(AppRuntime appRuntime, ServletContainer servletContainer) {
        this.mAppRuntime = appRuntime;
        this.container = servletContainer;
    }

    public void notifyObserver(Intent intent, int i, boolean z, Bundle bundle, Class<? extends BusinessObserver> cls) {
        throw new RuntimeException("Stub!");
    }

    protected void onCreate() {
    }

    protected void onDestroy() {
    }

    public abstract void service(Intent intent);
}
