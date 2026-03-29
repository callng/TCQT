package mqq.app;

import android.content.Intent;
import com.tencent.qphone.base.remote.FromServiceMsg;
import com.tencent.qphone.base.remote.ToServiceMsg;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MSFServlet extends Servlet {
    public static final AtomicInteger APP_SEQ_FACTORY;
    public static final String TAG = "MSFServlet";

    static {
        SecureRandom secureRandom = new SecureRandom();
        int nextInt = secureRandom.nextInt(1000) + 1;
        APP_SEQ_FACTORY = new AtomicInteger(nextInt);
    }

    protected <T> T decodePacket(byte[] bArr, String str, T t) {
        throw new RuntimeException("Stub!");
    }

    public String[] getPreferSSOCommands() {
        return null;
    }

    protected void onCreate() {
        throw new RuntimeException("Stub!");
    }

    Intent onReceive(FromServiceMsg fromServiceMsg) {
        throw new RuntimeException("Stub!");
    }

    public abstract void onReceive(Intent intent, FromServiceMsg fromServiceMsg);

    public abstract void onSend(Intent intent, Packet packet);

    Intent removeRequest(int i) {
        throw new RuntimeException("Stub!");
    }

    protected void sendToMSF(Intent intent, ToServiceMsg toServiceMsg) {
        throw new RuntimeException("Stub!");
    }

    public void service(Intent intent) {
        throw new RuntimeException("Stub!");
    }
}
