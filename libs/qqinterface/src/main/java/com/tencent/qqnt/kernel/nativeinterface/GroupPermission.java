package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class GroupPermission implements Serializable {
    public int key;
    public GroupAuthority authority = new GroupAuthority();

    public GroupAuthority getAuthority() {
        return this.authority;
    }

    public int getKey() {
        return this.key;
    }
}
