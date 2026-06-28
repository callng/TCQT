package com.tencent.mobileqq.data.troop;

import org.jetbrains.annotations.NotNull;
import kotlin.jvm.internal.Intrinsics;

public final class TroopMemberSpecialTitleInfo {
    public static final int SPECIAL_TITLE_EXPIRE_SOON_TIME = 259200;
    private int expireTimeSec;

    @NotNull
    private final String friendNick;

    @NotNull
    private String specialTitle;

    @NotNull
    private final String troopUin;

    @NotNull
    private final String uin;

    public TroopMemberSpecialTitleInfo(@NotNull String str, @NotNull String str2, @NotNull String str3, @NotNull String str4, int i) {
        Intrinsics.checkNotNullParameter(str, "troopUin");
        Intrinsics.checkNotNullParameter(str2, "uin");
        Intrinsics.checkNotNullParameter(str3, "friendNick");
        Intrinsics.checkNotNullParameter(str4, "specialTitle");
        this.troopUin = str;
        this.uin = str2;
        this.friendNick = str3;
        this.specialTitle = str4;
        this.expireTimeSec = i;
    }

    @NotNull
    public String component1() {
        return this.troopUin;
    }

    @NotNull
    public String component2() {
        return this.uin;
    }

    @NotNull
    public String component3() {
        return this.friendNick;
    }

    @NotNull
    public String component4() {
        return this.specialTitle;
    }

    public int component5() {
        return this.expireTimeSec;
    }

    public int getExpireTimeSec() {
        return this.expireTimeSec;
    }

    @NotNull
    public String getFriendNick() {
        return this.friendNick;
    }

    @NotNull
    public String getSpecialTitle() {
        return this.specialTitle;
    }

    @NotNull
    public String getTroopUin() {
        return this.troopUin;
    }

    @NotNull
    public String getUin() {
        return this.uin;
    }

    public boolean isExpired() {
        throw new RuntimeException("Stub!");
    }

    public final boolean isExpiredSoon() {
        throw new RuntimeException("Stub!");
    }

    public void setExpireTimeSec(int i) {
        this.expireTimeSec = i;
    }
}
