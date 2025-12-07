package mqq.inject;

public interface ISigInject {
    boolean banSkeyAccess();

    String getFakeSkey(String str);

    void getLoginSig(long j, long j2, ILoginMainTicketCallback iLoginMainTicketCallback);
}
