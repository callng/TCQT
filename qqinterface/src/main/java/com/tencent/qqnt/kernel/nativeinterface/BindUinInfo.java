package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class BindUinInfo {
    public ArrayList<UinInfo> uinInfoList = new ArrayList<>();
    public String unbindWording = "";

    public ArrayList<UinInfo> getUinInfoList() {
        return this.uinInfoList;
    }

    public String getUnbindWording() {
        return this.unbindWording;
    }
}
