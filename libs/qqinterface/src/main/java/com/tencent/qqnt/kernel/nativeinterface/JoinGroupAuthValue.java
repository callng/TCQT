package com.tencent.qqnt.kernel.nativeinterface;

public final class JoinGroupAuthValue {
    public int qqLevel;
    public int sexType;
    public JoinGroupUinAge uinAge = new JoinGroupUinAge();

    public int getQqLevel() {
        return this.qqLevel;
    }

    public int getSexType() {
        return this.sexType;
    }

    public JoinGroupUinAge getUinAge() {
        return this.uinAge;
    }
}
