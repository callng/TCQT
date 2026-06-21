package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public final class GroupExtInfoMuteTask {
    public int muteTaskSwitch;
    public String activeTaskId = "";
    public ArrayList<GroupMuteTask> muteTasks = new ArrayList<>();

    public String getActiveTaskId() {
        return this.activeTaskId;
    }

    public int getMuteTaskSwitch() {
        return this.muteTaskSwitch;
    }

    public ArrayList<GroupMuteTask> getMuteTasks() {
        return this.muteTasks;
    }
}
