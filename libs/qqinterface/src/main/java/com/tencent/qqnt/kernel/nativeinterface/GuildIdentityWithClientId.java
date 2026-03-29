package com.tencent.qqnt.kernel.nativeinterface;

public final class GuildIdentityWithClientId {
    public int clientId;
    public GuildIdentity identities = new GuildIdentity();

    public int getClientId() {
        return this.clientId;
    }

    public GuildIdentity getIdentities() {
        return this.identities;
    }
}
