package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class GuildClientIdentityBytes {
    public int clientId;
    public ArrayList<GuildIdentity> identitys = new ArrayList<>();
    public ArrayList<GuildIdentityWithClientId> defaultIdentities = new ArrayList<>();

    public int getClientId() {
        return this.clientId;
    }

    public ArrayList<GuildIdentityWithClientId> getDefaultIdentities() {
        return this.defaultIdentities;
    }

    public ArrayList<GuildIdentity> getIdentitys() {
        return this.identitys;
    }
}
