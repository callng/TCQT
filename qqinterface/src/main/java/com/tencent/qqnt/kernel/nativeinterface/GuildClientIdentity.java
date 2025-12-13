package com.tencent.qqnt.kernel.nativeinterface;

public final class GuildClientIdentity {
    public int clientId;
    public String desc;
    public GuildClientIdentityBytes identityBytes;

    public GuildClientIdentity() {
        this.desc = "";
        this.identityBytes = new GuildClientIdentityBytes();
    }

    public int getClientId() {
        return this.clientId;
    }

    public String getDesc() {
        return this.desc;
    }

    public GuildClientIdentityBytes getIdentityBytes() {
        return this.identityBytes;
    }

    public GuildClientIdentity(int i, String str, GuildClientIdentityBytes guildClientIdentityBytes) {
        this.desc = "";
        new GuildClientIdentityBytes();
        this.clientId = i;
        this.desc = str;
        this.identityBytes = guildClientIdentityBytes;
    }
}
