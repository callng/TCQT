package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupMuteTask {
    public long beginTime;
    public int cycleMode;
    public boolean enabled;
    public long endTime;
    public int weekMask;
    public String taskId = "";
    public String planId = "";

    public long getBeginTime() {
        return this.beginTime;
    }

    public int getCycleMode() {
        return this.cycleMode;
    }

    public boolean getEnabled() {
        return this.enabled;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public String getPlanId() {
        return this.planId;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public int getWeekMask() {
        return this.weekMask;
    }
}
