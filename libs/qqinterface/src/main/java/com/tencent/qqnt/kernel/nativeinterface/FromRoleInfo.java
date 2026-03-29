package com.tencent.qqnt.kernel.nativeinterface;

public final class FromRoleInfo {
    public int color;
    public String name;
    public long roleId;

    public FromRoleInfo() {
        this.name = "";
    }

    public int getColor() {
        return this.color;
    }

    public String getName() {
        return this.name;
    }

    public long getRoleId() {
        return this.roleId;
    }

    public FromRoleInfo(long j, String str, int i) {
        this.roleId = j;
        this.name = str;
        this.color = i;
    }
}
