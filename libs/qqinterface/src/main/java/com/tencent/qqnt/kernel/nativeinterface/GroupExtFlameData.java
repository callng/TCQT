package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class GroupExtFlameData {
    public ArrayList<Integer> dayNums = new ArrayList<>();
    public boolean isDisplayDayNum;
    public int state;
    public int switchState;
    public long updateTime;
    public int version;

    public ArrayList<Integer> getDayNums() {
        return this.dayNums;
    }

    public boolean getIsDisplayDayNum() {
        return this.isDisplayDayNum;
    }

    public int getState() {
        return this.state;
    }

    public int getSwitchState() {
        return this.switchState;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public int getVersion() {
        return this.version;
    }
}
