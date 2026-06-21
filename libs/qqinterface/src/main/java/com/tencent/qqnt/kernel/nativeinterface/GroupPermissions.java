package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;
import java.util.ArrayList;

public final class GroupPermissions implements Serializable {
    public int reqAllFlag;
    public ArrayList<GroupPermission> permissions = new ArrayList<>();

    public ArrayList<GroupPermission> getPermissions() {
        return this.permissions;
    }

    public int getReqAllFlag() {
        return this.reqAllFlag;
    }
}
