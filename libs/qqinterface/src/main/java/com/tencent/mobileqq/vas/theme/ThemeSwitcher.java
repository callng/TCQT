package com.tencent.mobileqq.vas.theme;

import java.util.concurrent.atomic.AtomicReference;

public class ThemeSwitcher {

    private static final AtomicReference<String> sRoamingThemeId;

    static {
        sRoamingThemeId = new AtomicReference<>(null);
    }

    public static String getRoamingThemeId() {
        return sRoamingThemeId.get();
    }

    public static void clearRoamingThemeId() {
        throw new RuntimeException("Stub!");
    }

    public static void setRoamingThemeId(String str) {
        sRoamingThemeId.set(str);
    }
}
