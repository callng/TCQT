package mqq.inject;

public class SigInjectManager implements ISigInject {

    private static final String TAG = "SkeyInjectManager";
    private static volatile SigInjectManager sInstance;

    public static SigInjectManager instance() {
        if (sInstance == null) {
            synchronized (SigInjectManager.class) {
                if (sInstance == null) {
                    sInstance = new SigInjectManager();
                }
            }
        }
        return sInstance;
    }

    @Override
    public boolean banSkeyAccess() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public String getFakeSkey(String str) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void getLoginSig(long j, long j2, ILoginMainTicketCallback iLoginMainTicketCallback) {
        throw new RuntimeException("Stub!");
    }
}
